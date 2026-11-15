package example.tenants;

import java.util.Map;

import io.temporal.activity.Activity;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"tenant-a", "tenant-b"})
    void shouldStartReadAndAuditOnlyTheConfiguredTenant(String tenant) throws Exception {
        var options = TestEnvironmentOptions.newBuilder().setWorkflowClientOptions(
            WorkflowClientOptions.newBuilder().setNamespace(tenant)
                .setIdentity(tenant + "/test-pod").build()).build();
        try (var environment = TestWorkflowEnvironment.newInstance(options)) {
            var route = new TenantRoute(tenant, tenant, tenant + "-work");
            var client = environment.getWorkflowClient();
            var jobs = new JobService(Map.of(tenant, new TenantClient(route, client)));
            var worker = environment.newWorker(route.taskQueue());
            worker.registerWorkflowImplementationTypes(JobWorkflowImpl.class);
            worker.registerActivitiesImplementations(new RecordingActivities(tenant));
            environment.start();

            var started = jobs.start(tenant, "job-1");
            // The in-memory test service advances workflow timers while waiting for this result.
            var workflow = client.newWorkflowStub(JobWorkflow.class, started.workflowId());
            WorkflowStub.fromTyped(workflow).getResult(JobResult.class);

            assertThat(started.temporalNamespace()).isEqualTo(tenant);
            assertThat(started.taskQueue()).isEqualTo(tenant + "-work");
            assertThat(jobs.status(tenant, "job-1")).isEqualTo("WORKFLOW_EXECUTION_STATUS_COMPLETED");
            assertThat(jobs.result(tenant, "job-1").executions()).hasSize(3).allSatisfy(record -> {
                assertThat(record.temporalNamespace()).isEqualTo(tenant);
                assertThat(record.taskQueue()).isEqualTo(tenant + "-work");
            });
            assertThat(jobs.workflowWorkers(tenant, "job-1")).containsExactly(tenant + "/test-pod");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant-a", "tenant-b"})
    void shouldRejectDuplicateJobWhileItIsWaitingForAWorker(String tenant) {
        var options = TestEnvironmentOptions.newBuilder().setWorkflowClientOptions(
            WorkflowClientOptions.newBuilder().setNamespace(tenant).build()).build();
        try (var environment = TestWorkflowEnvironment.newInstance(options)) {
            var jobs = new JobService(Map.of(tenant, new TenantClient(
                new TenantRoute(tenant, tenant, tenant + "-work"), environment.getWorkflowClient())));
            jobs.start(tenant, "same-job");

            assertThatThrownBy(() -> jobs.start(tenant, "same-job"))
                .isInstanceOf(WorkflowExecutionAlreadyStarted.class);
        }
    }

    private record RecordingActivities(String tenant) implements JobActivities {
        @Override
        public ExecutionRecord recordStep(JobInput input, String step) {
            var info = Activity.getExecutionContext().getInfo();
            return new ExecutionRecord(tenant, info.getWorkflowId(), info.getNamespace(),
                info.getActivityTaskQueue(), tenant, "test-pod", step, info.getAttempt());
        }

        @Override
        public ExecutionRecord process(JobInput input) {
            return recordStep(input, "processed");
        }
    }
}
