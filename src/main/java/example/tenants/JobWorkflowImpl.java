package example.tenants;

import java.time.Duration;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class JobWorkflowImpl implements JobWorkflow {
    @Override
    public JobResult run(JobInput input) {
        JobActivities activities = Workflow.newActivityStub(
            JobActivities.class,
            ActivityOptions.newBuilder()
                .setTaskQueue(Workflow.getInfo().getTaskQueue())
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setScheduleToCloseTimeout(Duration.ofMinutes(10))
                .setHeartbeatTimeout(Duration.ofSeconds(5))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(20).build())
                .setDisableEagerExecution(true)
                .build()
        );
        ExecutionRecord started = activities.recordStep(input, "started");
        Workflow.sleep(Duration.ofSeconds(20));
        ExecutionRecord processed = activities.process(input);
        ExecutionRecord completed = activities.recordStep(input, "completed");
        return new JobResult(input.tenantId(), List.of(started, processed, completed));
    }
}
