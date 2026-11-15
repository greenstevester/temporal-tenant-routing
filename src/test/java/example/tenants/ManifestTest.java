package example.tenants;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestTest {
    @ParameterizedTest
    @ValueSource(strings = {"local", "cloud"})
    void shouldRenderBothTenantDeploymentsWithoutCredentialsOrUnresolvedPlaceholders(String mode)
            throws Exception {
        String render = """
            source scripts/common.sh
            load_mode "$1"
            DEMO_IMAGE=temporal-tenant-routing:test
            render_app
            printf '\n---\n'
            render_worker tenant-a "$TENANT_A_NAMESPACE" "$TENANT_A_TARGET"
            printf '\n---\n'
            render_worker tenant-b "$TENANT_B_NAMESPACE" "$TENANT_B_TARGET"
            scripts/render-policies.sh "$1"
            """;
        var builder = new ProcessBuilder("bash", "-c", render, "render", mode);
        builder.environment().putAll(Map.of(
            "TENANT_A_NAMESPACE", "tenant-a.example", "TENANT_B_NAMESPACE", "tenant-b.example",
            "TENANT_A_KEY", "never-render-this-key", "TENANT_B_KEY", "never-render-this-key",
            "TENANT_A_WORKER_KEY", "never-render-this-key", "TENANT_B_WORKER_KEY", "never-render-this-key"));
        var process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String errors = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(errors).isZero();
        assertThat(output).doesNotContain("__", "never-render-this-key");
        var deployments = new ArrayList<String>();
        for (Object document : new Yaml().loadAll(output)) {
            var resource = (Map<?, ?>) document;
            if ("Deployment".equals(resource.get("kind"))) {
                var metadata = (Map<?, ?>) resource.get("metadata");
                deployments.add(metadata.get("namespace").toString());
            }
        }
        assertThat(deployments).containsExactlyInAnyOrder("application", "tenant-a", "tenant-b");
    }

    @ParameterizedTest
    @ValueSource(strings = {"namespaces.yaml", "local-temporal.yaml", "access-probe.yaml.tpl"})
    void shouldParseStaticKubernetesDocuments(String filename) throws Exception {
        String content = Files.readString(Path.of("k8s", filename));
        var resources = new ArrayList<Object>();
        new Yaml().loadAll(content).forEach(resources::add);
        assertThat(resources).isNotEmpty().allSatisfy(resource -> {
            assertThat(((Map<?, ?>) resource).get("apiVersion")).isNotNull();
            assertThat(((Map<?, ?>) resource).get("kind")).isNotNull();
        });
    }
}
