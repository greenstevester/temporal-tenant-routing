package example.tenants;

import java.util.List;
import java.util.Map;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingTest {
    @Test
    void shouldResolveTenantFromAuthenticatedAuthority() {
        var authentication = new UsernamePasswordAuthenticationToken("bob", "unused",
            List.of(new SimpleGrantedAuthority("tenant:tenant-b")));
        assertThat(new TenantResolver().resolve(authentication)).isEqualTo("tenant-b");
    }

    @Test
    void shouldRejectUnauthenticatedRequest() {
        var authentication = new UsernamePasswordAuthenticationToken("alice", "unused");
        assertThatThrownBy(() -> new TenantResolver().resolve(authentication))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectAmbiguousTenantMembership() {
        var authentication = new UsernamePasswordAuthenticationToken("alice", "unused", List.of(
            new SimpleGrantedAuthority("tenant:tenant-a"), new SimpleGrantedAuthority("tenant:tenant-b")));
        assertThatThrownBy(() -> new TenantResolver().resolve(authentication))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectUnknownTenantMembership() {
        var authentication = new UsernamePasswordAuthenticationToken("alice", "unused",
            List.of(new SimpleGrantedAuthority("tenant:other")));
        assertThatThrownBy(() -> new TenantResolver().resolve(authentication))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAcceptMatchingCloudRoute() {
        assertThatCode(() -> new TenantRoute("tenant-a", "tenant-a.acme123", "tenant-a-work"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectQueueForAnotherTenant() {
        assertThatThrownBy(() -> new TenantRoute("tenant-a", "tenant-a", "tenant-b-work"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNamespaceForAnotherTenant() {
        assertThatThrownBy(() -> new TenantRoute("tenant-a", "tenant-b.acme123", "tenant-a-work"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectWorkerInWrongKubernetesNamespace() {
        var route = new TenantRoute("tenant-a", "tenant-a", "tenant-a-work");
        assertThatThrownBy(() -> new WorkerLocation(route, "tenant-b", "worker-1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAnotherTenantsActivityInput() {
        var location = new WorkerLocation(new TenantRoute("tenant-a", "tenant-a", "tenant-a-work"),
            "tenant-a", "worker-1");
        assertThatThrownBy(() -> location.requireTenant("tenant-b")).isInstanceOf(TenantMismatchException.class);
        assertThat(location.identity()).isEqualTo("tenant-a/worker-1");
    }

    @Test
    void shouldRefuseCredentialOnUnencryptedConnection() {
        assertThatThrownBy(() -> Connections.options("localhost:7233", "secret", true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireCredentialInSecuredMode() {
        assertThatThrownBy(() -> Connections.options("service:7233", "", false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptExplicitCredentialFreeLocalMode() {
        assertThatCode(() -> Connections.options("temporal.temporal-system.svc.cluster.local:7233", "", true))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldKeepSameJobIdentifierSeparateBetweenTenants() {
        assertThat(JobService.workflowId("tenant-a", "job-1"))
            .isNotEqualTo(JobService.workflowId("tenant-b", "job-1"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../tenant-a/job-1", "a/b", "with space", "x\nheader"})
    void shouldRejectInvalidJobIdentifiers(String jobId) {
        assertThatThrownBy(() -> JobService.workflowId("tenant-a", jobId))
            .isInstanceOf(InvalidJobIdException.class);
    }

    @Test
    void shouldRejectUnconfiguredTenantBeforeContactingTemporal() {
        assertThatThrownBy(() -> new JobService(Map.of()).start("other", "job-1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAcceptExplicitPermissionRejectionAsDenialEvidence() {
        assertThatCode(() -> AccessProbe.requireDenied("poll", () -> {
            throw Status.PERMISSION_DENIED.asRuntimeException();
        })).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTimeoutAsDenialEvidence() {
        assertThatThrownBy(() -> AccessProbe.requireDenied("poll", () -> {
            throw Status.DEADLINE_EXCEEDED.asRuntimeException();
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailProbeWhenCrossTenantRequestSucceeds() {
        assertThatThrownBy(() -> AccessProbe.requireDenied("poll", () -> {}))
            .isInstanceOf(IllegalStateException.class);
    }
}
