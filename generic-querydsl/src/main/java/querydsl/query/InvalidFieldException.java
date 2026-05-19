package querydsl.query;

/**
 * Exception thrown when a field doesn't exist in the entity or cannot be accessed.
 * 
 * <p>This exception is thrown by QueryPredicateBuilder when:
 * <ul>
 *   <li>A field path cannot be resolved (e.g., "nonExistentField")</li>
 *   <li>A nested field path is invalid (e.g., "role.invalidField")</li>
 *   <li>A field cannot be accessed due to reflection errors</li>
 * </ul>
 * 
 * <p>The exception message includes both the field name and the entity class
 * for easier debugging.
 * 
 * @see QueryPredicateBuilder
 */
public class InvalidFieldException extends RuntimeException {
    
    public InvalidFieldException(String message) {
        super(message);
    }
    
    public InvalidFieldException(String fieldName, Class<?> entityClass) {
        super(String.format("Field '%s' does not exist in entity '%s'", fieldName, entityClass.getSimpleName()));
    }
}
