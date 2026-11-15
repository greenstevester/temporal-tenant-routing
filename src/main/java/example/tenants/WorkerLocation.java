package example.tenants;

public record WorkerLocation(TenantRoute route, String podNamespace, String podName) {
    public WorkerLocation {
        if (!route.tenantId().equals(podNamespace)) {
            throw new IllegalArgumentException("Worker tenant and Kubernetes namespace differ");
        }
        if (podName == null || podName.isBlank()) {
            throw new IllegalArgumentException("Pod name is required");
        }
    }

    public void requireTenant(String tenantId) {
        if (!route.tenantId().equals(tenantId)) {
            throw new TenantMismatchException("Task tenant does not match this worker");
        }
    }

    public String identity() {
        return podNamespace + "/" + podName;
    }
}
