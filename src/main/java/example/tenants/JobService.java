package example.tenants;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.security.access.AccessDeniedException;

public class JobService {
    private final Map<String, TenantClient> tenants;

    public JobService(Map<String, TenantClient> tenants) {
        this.tenants = Map.copyOf(tenants);
    }

    public StartedJob start(String tenantId, String jobId) {
        TenantClient tenant = requireTenant(tenantId);
        String workflowId = workflowId(tenantId, jobId);
        JobWorkflow workflow = tenant.client().newWorkflowStub(JobWorkflow.class,
            WorkflowOptions.newBuilder().setWorkflowId(workflowId)
                .setTaskQueue(tenant.route().taskQueue())
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .build());
        WorkflowClient.start(workflow::run, new JobInput(tenantId, jobId));
        return new StartedJob(workflowId, tenant.route().namespace(), tenant.route().taskQueue());
    }

    public String status(String tenantId, String jobId) {
        TenantClient tenant = requireTenant(tenantId);
        return tenant.client().getWorkflowServiceStubs().blockingStub().describeWorkflowExecution(
            DescribeWorkflowExecutionRequest.newBuilder().setNamespace(tenant.route().namespace())
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId(tenantId, jobId)))
                .build()).getWorkflowExecutionInfo().getStatus().name();
    }

    public JobResult result(String tenantId, String jobId) throws TimeoutException {
        TenantClient tenant = requireTenant(tenantId);
        JobWorkflow workflow = tenant.client().newWorkflowStub(JobWorkflow.class, workflowId(tenantId, jobId));
        return WorkflowStub.fromTyped(workflow).getResult(1, TimeUnit.SECONDS, JobResult.class);
    }

    public List<String> workflowWorkers(String tenantId, String jobId) {
        TenantClient tenant = requireTenant(tenantId);
        return tenant.client().fetchHistory(workflowId(tenantId, jobId)).getHistory().getEventsList().stream()
            .filter(event -> event.hasWorkflowTaskStartedEventAttributes())
            .map(event -> event.getWorkflowTaskStartedEventAttributes().getIdentity()).distinct().toList();
    }

    public static String workflowId(String tenantId, String jobId) {
        if (jobId == null || !jobId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new InvalidJobIdException();
        }
        return tenantId + "/job/" + jobId;
    }

    private TenantClient requireTenant(String tenantId) {
        TenantClient tenant = tenants.get(tenantId);
        if (tenant == null) {
            throw new AccessDeniedException("Tenant is not configured");
        }
        return tenant;
    }

    public record StartedJob(String workflowId, String temporalNamespace, String taskQueue) {}
}
