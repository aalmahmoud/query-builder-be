package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import querydsl.query.QueryCondition;

/**
 * Type-safe interface for handling computed/virtual fields
 * This interface uses generics to provide type safety for Q-entity types
 * 
 * @param <T> The entity type
 * @param <Q> The QueryDSL Q-entity type
 */
public interface TypedComputedFieldHandler<T, Q> {
    
    /**
     * Gets the entity class this handler supports
     * 
     * @return The entity class
     */
    Class<T> getEntityClass();
    
    /**
     * Gets the field name this handler supports
     * 
     * @return The field name
     */
    String getFieldName();
    
    /**
     * Builds a QueryDSL predicate for the computed field
     * 
     * @param qEntity The QueryDSL Q-entity root (type-safe)
     * @param condition The query condition
     * @return Predicate for the computed field
     */
    Predicate buildPredicate(Q qEntity, QueryCondition condition);
}

