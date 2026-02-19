package querydsl.exception;

/**
 * Exception thrown when validation fails.
 * 
 * <p>This exception should be thrown when business logic validation fails,
 * such as invalid data formats, constraint violations, or business rule violations.
 * 
 * <p>The global exception handler will map this to HTTP 400 BAD REQUEST.
 */
public class ValidationException extends RuntimeException {
    
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
