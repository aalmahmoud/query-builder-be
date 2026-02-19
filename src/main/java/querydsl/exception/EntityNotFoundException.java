package querydsl.exception;

/**
 * Exception thrown when an entity is not found in the database.
 * 
 * <p>This exception should be thrown when attempting to access an entity
 * that does not exist, typically in service methods when fetching by ID.
 * 
 * <p>The global exception handler will map this to HTTP 404 NOT FOUND.
 */
public class EntityNotFoundException extends RuntimeException {
    
    public EntityNotFoundException(String message) {
        super(message);
    }
    
    public EntityNotFoundException(String entityName, Object id) {
        super(String.format("%s not found with id: %s", entityName, id));
    }
    
    public EntityNotFoundException(String entityName, String fieldName, Object value) {
        super(String.format("%s not found with %s: %s", entityName, fieldName, value));
    }
}
