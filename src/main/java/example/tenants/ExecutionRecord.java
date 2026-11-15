package example.tenants;

public record ExecutionRecord(
        String tenantId, String workflowId, String temporalNamespace, String taskQueue,
        String podNamespace, String podName, String step, int attempt) {}
