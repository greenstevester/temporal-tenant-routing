package example.tenants;

import java.util.List;

public record JobResult(String tenantId, List<ExecutionRecord> executions) {}
