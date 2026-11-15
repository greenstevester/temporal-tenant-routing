package example.tenants;

import java.util.concurrent.atomic.AtomicInteger;

import io.temporal.activity.Activity;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTest {
    @Test
    void shouldRouteBothTaskKindsAndReplayWithoutRepeatingCompletedActivities() throws Exception {
        try (TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance()) {
            AtomicInteger activityCalls = new AtomicInteger();
            for (String tenant : new String[] {"tenant-a", "tenant-b"}) {
                var worker = environment.newWorker(tenant + "-work");
                worker.registerWorkflowImplementationTypes(JobWorkflowImpl.class);
                worker.registerActivitiesImplementations(new RecordingActivities(tenant, activityCalls));
            }
            environment.start();
            for (String tenant : new String[] {"tenant-a", "tenant-b"}) {
                JobWorkflow workflow = environment.getWorkflowClient().newWorkflowStub(JobWorkflow.class,
                    WorkflowOptions.newBuilder().setWorkflowId(tenant + "/job/test")
                        .setTaskQueue(tenant + "-work").build());
                var execution = WorkflowClient.start(workflow::run, new JobInput(tenant, "test"));
                JobResult result = io.temporal.client.WorkflowStub.fromTyped(workflow).getResult(JobResult.class);
                assertThat(result.executions()).allSatisfy(record -> {
                    assertThat(record.tenantId()).isEqualTo(tenant);
                    assertThat(record.podNamespace()).isEqualTo(tenant);
                    assertThat(record.taskQueue()).isEqualTo(tenant + "-work");
                });
                int beforeReplay = activityCalls.get();
                WorkflowReplayer.replayWorkflowExecution(environment.getWorkflowExecutionHistory(execution),
                    JobWorkflowImpl.class);
                assertThat(activityCalls.get()).isEqualTo(beforeReplay);
            }
        }
    }

    private record RecordingActivities(String tenant, AtomicInteger calls) implements JobActivities {
        @Override
        public ExecutionRecord recordStep(JobInput input, String step) {
            if (!tenant.equals(input.tenantId())) {
                throw new AssertionError("Wrong worker received task");
            }
            calls.incrementAndGet();
            var info = Activity.getExecutionContext().getInfo();
            return new ExecutionRecord(tenant, info.getWorkflowId(), info.getNamespace(),
                info.getActivityTaskQueue(), tenant, "test-worker", step, info.getAttempt());
        }

        @Override
        public ExecutionRecord process(JobInput input) {
            return recordStep(input, "processed");
        }
    }
}
