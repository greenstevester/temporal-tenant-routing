package example.tenants;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("worker")
public class WorkerConfiguration {
    @Bean
    WorkerLocation workerLocation(
            @Value("${TENANT_ID}") String tenant,
            @Value("${TEMPORAL_NAMESPACE}") String namespace,
            @Value("${TEMPORAL_TASK_QUEUE}") String queue,
            @Value("${POD_NAMESPACE}") String podNamespace,
            @Value("${POD_NAME}") String podName) {
        return new WorkerLocation(new TenantRoute(tenant, namespace, queue), podNamespace, podName);
    }

    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workerConnection(
            @Value("${TEMPORAL_TARGET}") String target,
            @Value("${TEMPORAL_API_KEY:}") String key,
            @Value("${LOCAL_TEMPORAL:false}") boolean isLocal) {
        return Connections.open(target, key, isLocal);
    }

    @Bean
    WorkflowClient workerClient(WorkflowServiceStubs connection, WorkerLocation location) {
        return WorkflowClient.newInstance(connection, WorkflowClientOptions.newBuilder()
            .setNamespace(location.route().namespace()).setIdentity(location.identity()).build());
    }

    @Bean
    DemoActivities jobActivities(WorkerLocation location) {
        return new DemoActivities(location);
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    WorkerFactory workers(WorkflowClient client, WorkerLocation location, DemoActivities activities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(location.route().taskQueue());
        worker.registerWorkflowImplementationTypes(JobWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return factory;
    }
}
