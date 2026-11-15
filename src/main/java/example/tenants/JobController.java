package example.tenants;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("app")
public class JobController {
    private final JobService jobs;
    private final TenantResolver resolver;

    public JobController(JobService jobs, TenantResolver resolver) {
        this.jobs = jobs;
        this.resolver = resolver;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobService.StartedJob> start(Authentication authentication, @RequestBody Request request) {
        return ResponseEntity.accepted().body(jobs.start(resolver.resolve(authentication), request.jobId()));
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, String> status(Authentication authentication, @PathVariable String jobId) {
        return Map.of("status", jobs.status(resolver.resolve(authentication), jobId));
    }

    @GetMapping("/jobs/{jobId}/result")
    public ResponseEntity<?> result(Authentication authentication, @PathVariable String jobId) {
        try {
            return ResponseEntity.ok(jobs.result(resolver.resolve(authentication), jobId));
        } catch (TimeoutException error) {
            return ResponseEntity.accepted().body(Map.of("status", "waiting"));
        }
    }

    @GetMapping("/jobs/{jobId}/workers")
    public Map<String, List<String>> workers(Authentication authentication, @PathVariable String jobId) {
        return Map.of("workflowWorkers", jobs.workflowWorkers(resolver.resolve(authentication), jobId));
    }

    public record Request(String jobId) {}
}
