package example.tenants;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface JobWorkflow {
    @WorkflowMethod
    JobResult run(JobInput input);
}
