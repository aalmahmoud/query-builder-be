package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import querydsl.query.QueryCondition;

/**
 * Interface for handling computed/virtual fields that don't exist in the database
 * These fields are derived from other database fields
 */
public interface ComputedFieldHandler {
    
    /**
     * Checks if this handler can handle the given entity class and field name
     * 
     * @param entityClass The entity class
     * @param fieldName The field name
     * @return true if this handler can handle the computed field
     */
    boolean supports(Class<?> entityClass, String fieldName);
    
    /**
     * Builds a QueryDSL predicate for the computed field
     * 
     * @param qEntity The QueryDSL Q-entity root
     * @param condition The query condition
     * @param entityClass The entity class
     * @return Predicate for the computed field, or null if not handled
     */
    Predicate buildPredicate(Object qEntity, QueryCondition condition, Class<?> entityClass);
}

