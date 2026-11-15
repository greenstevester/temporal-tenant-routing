package example.tenants;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface JobActivities {
    @ActivityMethod
    ExecutionRecord recordStep(JobInput input, String step);

    @ActivityMethod
    ExecutionRecord process(JobInput input);
}
