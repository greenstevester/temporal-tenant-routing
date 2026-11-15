package example.tenants;

import java.util.Map;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Profile("app")
public class RequestErrors {
    private static final Logger LOG = LoggerFactory.getLogger(RequestErrors.class);

    @ExceptionHandler(InvalidJobIdException.class)
    public ResponseEntity<?> invalid(InvalidJobIdException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(WorkflowExecutionAlreadyStarted.class)
    public ResponseEntity<?> duplicate() {
        return ResponseEntity.status(409).body(Map.of("error", "Job already exists"));
    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("error", "Job not found"));
    }

    @ExceptionHandler({WorkflowServiceException.class, StatusRuntimeException.class})
    public ResponseEntity<?> temporal(RuntimeException error) {
        if (Status.fromThrowable(error).getCode() == Status.Code.NOT_FOUND) {
            return missing();
        }
        LOG.error("Temporal request failed", error);
        return ResponseEntity.status(503).body(Map.of("error", "Temporal request could not complete"));
    }
}
