package example.tenants;

import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestActivityEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoActivitiesTest {
    @Test
    void shouldRecordWorkerLocationFromWorkerConfiguration() {
        TestActivityEnvironment environment = TestActivityEnvironment.newInstance();
        try {
            environment.registerActivitiesImplementations(activities());
            var stub = environment.newActivityStub(JobActivities.class);
            var result = stub.recordStep(new JobInput("tenant-a", "job-1"), "started");
            assertThat(result.podNamespace()).isEqualTo("tenant-a");
            assertThat(result.podName()).isEqualTo("pod-a");
        } finally {
            environment.close();
        }
    }

    @Test
    void shouldRejectWrongTenantWithoutRetrying() {
        TestActivityEnvironment environment = TestActivityEnvironment.newInstance();
        try {
            environment.registerActivitiesImplementations(activities());
            var stub = environment.newActivityStub(JobActivities.class);
            assertThatThrownBy(() -> stub.recordStep(new JobInput("tenant-b", "job-1"), "started"))
                .hasCauseInstanceOf(ApplicationFailure.class)
                .satisfies(error -> assertThat(((ApplicationFailure) error.getCause()).isNonRetryable()).isTrue());
        } finally {
            environment.close();
        }
    }

    @Test
    void shouldHeartbeatAndCompleteTheDemonstrationStep() {
        TestActivityEnvironment environment = TestActivityEnvironment.newInstance();
        try {
            environment.registerActivitiesImplementations(activities());
            var result = environment.newActivityStub(JobActivities.class).process(new JobInput("tenant-a", "job-1"));
            assertThat(result.step()).isEqualTo("processed");
        } finally {
            environment.close();
        }
    }

    private DemoActivities activities() {
        return new DemoActivities(new WorkerLocation(
            new TenantRoute("tenant-a", "tenant-a", "tenant-a-work"), "tenant-a", "pod-a"));
    }
}
