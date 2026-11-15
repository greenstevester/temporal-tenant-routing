package example.tenants;

import java.util.Set;

public record TenantRoute(String tenantId, String namespace, String taskQueue) {
    public TenantRoute {
        if (!Set.of("tenant-a", "tenant-b").contains(tenantId)) {
            throw new IllegalArgumentException("Unknown tenant");
        }
        if (namespace == null || !(namespace.equals(tenantId) || namespace.startsWith(tenantId + "."))) {
            throw new IllegalArgumentException("Temporal namespace does not belong to tenant");
        }
        if (!(tenantId + "-work").equals(taskQueue)) {
            throw new IllegalArgumentException("Task queue does not belong to tenant");
        }
    }
}
