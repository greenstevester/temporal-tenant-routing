package example.tenants;

public class InvalidJobIdException extends RuntimeException {
    public InvalidJobIdException() {
        super("Job identifier must contain 1 to 64 letters, digits, hyphens or underscores");
    }
}
