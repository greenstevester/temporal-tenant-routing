package example.tenants;

import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public final class Connections {
    private Connections() {}

    public static WorkflowServiceStubs open(String target, String key, boolean isLocal) {
        return WorkflowServiceStubs.newServiceStubs(options(target, key, isLocal));
    }

    public static WorkflowServiceStubsOptions options(String target, String key, boolean isLocal) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Temporal address is required");
        }
        if (!isLocal && (key == null || key.isBlank())) {
            throw new IllegalArgumentException("Secured mode requires a Temporal access key");
        }
        if (isLocal && key != null && !key.isBlank()) {
            throw new IllegalArgumentException("Refusing to send a credential over a local unencrypted connection");
        }
        WorkflowServiceStubsOptions.Builder options = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(target).setEnableHttps(!isLocal);
        if (!isLocal) {
            options.addApiKey(() -> key);
        }
        return options.build();
    }
}
