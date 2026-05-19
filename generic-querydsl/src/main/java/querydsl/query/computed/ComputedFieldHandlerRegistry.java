package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import querydsl.query.QueryCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for computed field handlers.
 * 
 * <p>Automatically discovers and registers all ComputedFieldHandler and TypedComputedFieldHandler
 * beans from the Spring context. Supports both interfaces for backward compatibility and type safety.
 * 
 * <p>Typed handlers are preferred for O(1) lookup, with fallback to untyped handlers for
 * backward compatibility. Handlers are registered automatically via Spring's @Autowired
 * dependency injection.
 * 
 * <p>Example usage:
 * <pre>
 * // Automatic registration via Spring
 * {@literal @}Component
 * public class MyHandler implements TypedComputedFieldHandler&lt;MyEntity, QMyEntity&gt; {
 *     // Handler implementation
 * }
 * </pre>
 * 
 * @see ComputedFieldHandler
 * @see TypedComputedFieldHandler
 */
@Component
@Slf4j
public class ComputedFieldHandlerRegistry {
    
    private final List<ComputedFieldHandler> handlers = new ArrayList<>();
    private final Map<Class<?>, Map<String, TypedComputedFieldHandler<?, ?>>> typedHandlers = new HashMap<>();
    
    /**
     * Auto-registers all ComputedFieldHandler beans from Spring context
     */
    @Autowired(required = false)
    public void setHandlers(List<ComputedFieldHandler> handlers) {
        if (handlers != null) {
            this.handlers.addAll(handlers);
            log.info("Auto-registered {} computed field handler(s)", handlers.size());
            handlers.forEach(h -> {
                log.debug("  - {} (implements ComputedFieldHandler)", h.getClass().getSimpleName());
                
                // If handler also implements TypedComputedFieldHandler, register it as typed
                if (h instanceof TypedComputedFieldHandler) {
                    @SuppressWarnings("unchecked")
                    TypedComputedFieldHandler<?, ?> typedHandler = (TypedComputedFieldHandler<?, ?>) h;
                    registerTypedHandler(typedHandler);
                }
            });
        }
    }
    
    /**
     * Auto-registers all TypedComputedFieldHandler beans from Spring context
     */
    @Autowired(required = false)
    public void setTypedHandlers(List<TypedComputedFieldHandler<?, ?>> typedHandlers) {
        if (typedHandlers != null) {
            typedHandlers.forEach(this::registerTypedHandler);
            log.info("Auto-registered {} typed computed field handler(s)", typedHandlers.size());
            typedHandlers.forEach(h -> log.debug("  - {} (implements TypedComputedFieldHandler)", 
                h.getClass().getSimpleName()));
        }
    }
    
    /**
     * Registers a typed handler in the internal map for O(1) lookup
     */
    private void registerTypedHandler(TypedComputedFieldHandler<?, ?> handler) {
        Class<?> entityClass = handler.getEntityClass();
        String fieldName = handler.getFieldName();
        
        typedHandlers.computeIfAbsent(entityClass, k -> new HashMap<>()).put(fieldName, handler);
        log.debug("Registered typed handler {} for {}.{}", 
            handler.getClass().getSimpleName(), entityClass.getSimpleName(), fieldName);
    }
    
    /**
     * Manually registers a computed field handler (for programmatic registration if needed)
     * Handlers are checked in registration order
     * 
     * @param handler The handler to register
     */
    public void register(ComputedFieldHandler handler) {
        if (handler != null && !handlers.contains(handler)) {
            handlers.add(handler);
            log.debug("Manually registered computed field handler: {}", handler.getClass().getSimpleName());
            
            // If handler also implements TypedComputedFieldHandler, register it as typed
            if (handler instanceof TypedComputedFieldHandler) {
                @SuppressWarnings("unchecked")
                TypedComputedFieldHandler<?, ?> typedHandler = (TypedComputedFieldHandler<?, ?>) handler;
                registerTypedHandler(typedHandler);
            }
        }
    }
    
    /**
     * Manually registers a typed computed field handler
     * 
     * @param handler The typed handler to register
     */
    public void registerTyped(TypedComputedFieldHandler<?, ?> handler) {
        if (handler != null) {
            registerTypedHandler(handler);
        }
    }
    
    /**
     * Builds a predicate for a computed field by finding the appropriate handler
     * First tries typed handlers (O(1) lookup), then falls back to untyped handlers (O(n) lookup)
     * 
     * @param qEntity The QueryDSL Q-entity root
     * @param condition The query condition
     * @param entityClass The entity class
     * @return Predicate for the computed field, or null if no handler found
     */
    public Predicate buildPredicate(Object qEntity, QueryCondition condition, Class<?> entityClass) {
        // First try typed handlers (O(1) lookup, type-safe)
        Map<String, TypedComputedFieldHandler<?, ?>> entityHandlers = typedHandlers.get(entityClass);
        if (entityHandlers != null) {
            TypedComputedFieldHandler<?, ?> typedHandler = entityHandlers.get(condition.getField());
            if (typedHandler != null) {
                log.debug("Using typed handler {} for field {} on entity {}", 
                    typedHandler.getClass().getSimpleName(), condition.getField(), entityClass.getSimpleName());
                try {
                    // Type-safe call - cast to the handler's Q-entity type
                    // The handler's buildPredicate method expects the correct Q-entity type
                    @SuppressWarnings("unchecked")
                    TypedComputedFieldHandler<Object, Object> handler = (TypedComputedFieldHandler<Object, Object>) typedHandler;
                    return handler.buildPredicate(qEntity, condition);
                } catch (ClassCastException e) {
                    log.warn("Type mismatch for typed handler {}: {}", 
                        typedHandler.getClass().getSimpleName(), e.getMessage());
                    // Fall through to untyped handler
                }
            }
        }
        
        // Fallback to untyped handlers (backward compatibility)
        for (ComputedFieldHandler handler : handlers) {
            if (handler.supports(entityClass, condition.getField())) {
                log.debug("Using untyped handler {} for field {} on entity {}", 
                    handler.getClass().getSimpleName(), condition.getField(), entityClass.getSimpleName());
                return handler.buildPredicate(qEntity, condition, entityClass);
            }
        }
        
        return null;
    }
}

