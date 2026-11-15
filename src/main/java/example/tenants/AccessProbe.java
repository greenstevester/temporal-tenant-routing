package example.tenants;

import java.util.concurrent.TimeUnit;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.taskqueue.v1.TaskQueue;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.PollActivityTaskQueueRequest;
import io.temporal.api.workflowservice.v1.PollWorkflowTaskQueueRequest;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("probe")
public class AccessProbe implements ApplicationRunner {
    private final ConfigurableApplicationContext context;
    private final String sourceTarget;
    private final String sourceNamespace;
    private final String target;
    private final String namespace;
    private final String key;
    private final String workflowId;
    private final boolean isLocal;
    private final boolean shouldDeny;

    public AccessProbe(ConfigurableApplicationContext context,
            @Value("${TENANT_B_TARGET}") String sourceTarget,
            @Value("${TENANT_B_NAMESPACE}") String sourceNamespace,
            @Value("${TENANT_A_TARGET}") String target,
            @Value("${TENANT_A_NAMESPACE}") String namespace,
            @Value("${TEMPORAL_API_KEY:}") String key,
            @Value("${PROBE_WORKFLOW_ID}") String workflowId,
            @Value("${LOCAL_TEMPORAL:false}") boolean isLocal,
            @Value("${EXPECT_DENIED:true}") boolean shouldDeny) {
        this.context = context;
        this.sourceTarget = sourceTarget;
        this.sourceNamespace = sourceNamespace;
        this.target = target;
        this.namespace = namespace;
        this.key = key;
        this.workflowId = workflowId;
        this.isLocal = isLocal;
        this.shouldDeny = shouldDeny;
    }

    @Override
    public void run(ApplicationArguments args) {
        WorkflowServiceStubs source = Connections.open(sourceTarget, key, isLocal);
        WorkflowServiceStubs destination = Connections.open(target, key, isLocal);
        try {
            source.blockingStub().withDeadlineAfter(10, TimeUnit.SECONDS).describeNamespace(
                DescribeNamespaceRequest.newBuilder().setNamespace(sourceNamespace).build());
            Runnable readHistory = () -> destination.blockingStub().withDeadlineAfter(5, TimeUnit.SECONDS)
                .getWorkflowExecutionHistory(GetWorkflowExecutionHistoryRequest.newBuilder()
                    .setNamespace(namespace)
                    .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId)).build());
            if (shouldDeny) {
                requireDenied("workflow polling", () -> destination.blockingStub()
                    .withDeadlineAfter(5, TimeUnit.SECONDS).pollWorkflowTaskQueue(
                        PollWorkflowTaskQueueRequest.newBuilder().setNamespace(namespace)
                            .setTaskQueue(TaskQueue.newBuilder().setName("tenant-a-work"))
                            .setIdentity("tenant-b/access-probe").build()));
                requireDenied("activity polling", () -> destination.blockingStub()
                    .withDeadlineAfter(5, TimeUnit.SECONDS).pollActivityTaskQueue(
                        PollActivityTaskQueueRequest.newBuilder().setNamespace(namespace)
                            .setTaskQueue(TaskQueue.newBuilder().setName("tenant-a-work"))
                            .setIdentity("tenant-b/access-probe").build()));
                requireDenied("history read", readHistory);
                System.out.println("SECURITY_DENIAL_PASSED: B could access B; all three A calls were rejected.");
            } else {
                if (!isLocal) {
                    throw new IllegalStateException("Permitted-access demonstration is restricted to local mode");
                }
                readHistory.run();
                System.out.println("LOCAL_SERVER_ACCEPTED_CROSS_TENANT_ACCESS: local mode has no authorization.");
            }
        } finally {
            source.shutdown();
            destination.shutdown();
        }
        context.close();
    }

    public static void requireDenied(String operation, Runnable request) {
        try {
            request.run();
        } catch (StatusRuntimeException error) {
            Status.Code code = error.getStatus().getCode();
            if (code == Status.Code.PERMISSION_DENIED || code == Status.Code.UNAUTHENTICATED) {
                return;
            }
            throw new IllegalStateException(operation + " failed without proving denied access: " + code, error);
        }
        throw new IllegalStateException(operation + " unexpectedly succeeded");
    }
}
