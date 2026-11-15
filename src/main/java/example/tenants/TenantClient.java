package example.tenants;

import io.temporal.client.WorkflowClient;

public record TenantClient(TenantRoute route, WorkflowClient client) {}
