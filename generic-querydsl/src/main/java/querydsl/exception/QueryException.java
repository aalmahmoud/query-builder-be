package querydsl.exception;

/**
 * Exception thrown when a query operation fails.
 * 
 * <p>This exception should be thrown when there are issues building or executing
 * queries, such as invalid field paths, type conversion failures, or query syntax errors.
 * 
 * <p>The global exception handler will map this to HTTP 400 BAD REQUEST.
 */
public class QueryException extends RuntimeException {
    
    public QueryException(String message) {
        super(message);
    }
    
    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
