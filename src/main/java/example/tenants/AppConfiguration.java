package example.tenants;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("app")
public class AppConfiguration {
    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs tenantAConnection(
            @Value("${TENANT_A_TARGET}") String target,
            @Value("${TENANT_A_KEY:}") String key,
            @Value("${LOCAL_TEMPORAL:false}") boolean isLocal) {
        return Connections.open(target, key, isLocal);
    }

    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs tenantBConnection(
            @Value("${TENANT_B_TARGET}") String target,
            @Value("${TENANT_B_KEY:}") String key,
            @Value("${LOCAL_TEMPORAL:false}") boolean isLocal) {
        return Connections.open(target, key, isLocal);
    }

    @Bean
    JobService jobService(
            @Qualifier("tenantAConnection") WorkflowServiceStubs a,
            @Qualifier("tenantBConnection") WorkflowServiceStubs b,
            @Value("${TENANT_A_NAMESPACE}") String namespaceA,
            @Value("${TENANT_B_NAMESPACE}") String namespaceB) {
        TenantRoute routeA = new TenantRoute("tenant-a", namespaceA, "tenant-a-work");
        TenantRoute routeB = new TenantRoute("tenant-b", namespaceB, "tenant-b-work");
        return new JobService(Map.of(
            "tenant-a", new TenantClient(routeA, client(a, namespaceA)),
            "tenant-b", new TenantClient(routeB, client(b, namespaceB))));
    }

    private WorkflowClient client(WorkflowServiceStubs connection, String namespace) {
        return WorkflowClient.newInstance(connection,
            WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }
}
