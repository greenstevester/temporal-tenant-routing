package example.tenants;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInfo;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoActivities implements JobActivities {
    private static final Logger LOG = LoggerFactory.getLogger(DemoActivities.class);
    private final WorkerLocation location;

    public DemoActivities(WorkerLocation location) {
        this.location = location;
    }

    @Override
    public ExecutionRecord recordStep(JobInput input, String step) {
        checkTenant(input);
        ActivityInfo info = Activity.getExecutionContext().getInfo();
        ExecutionRecord record = new ExecutionRecord(
            input.tenantId(), info.getWorkflowId(), info.getNamespace(), info.getActivityTaskQueue(),
            location.podNamespace(), location.podName(), step, info.getAttempt());
        LOG.info("execution tenant={} workflow={} namespace={} queue={} podNamespace={} pod={} step={} attempt={}",
            record.tenantId(), record.workflowId(), record.temporalNamespace(), record.taskQueue(),
            record.podNamespace(), record.podName(), record.step(), record.attempt());
        return record;
    }

    @Override
    public ExecutionRecord process(JobInput input) {
        checkTenant(input);
        recordStep(input, "processing");
        // ponytail: this demonstration has no business side effect; replace with work safe to retry.
        for (int second = 0; second < 10; second++) {
            Activity.getExecutionContext().heartbeat(second);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                LOG.warn("Activity interrupted tenant={} job={}", input.tenantId(), input.jobId());
                throw Activity.wrap(error);
            }
        }
        return recordStep(input, "processed");
    }

    private void checkTenant(JobInput input) {
        try {
            location.requireTenant(input.tenantId());
        } catch (TenantMismatchException error) {
            LOG.error("Rejected task tenant={} expected={} job={}",
                input.tenantId(), location.route().tenantId(), input.jobId());
            throw ApplicationFailure.newNonRetryableFailure(error.getMessage(), "TenantMismatch");
        }
    }
}
