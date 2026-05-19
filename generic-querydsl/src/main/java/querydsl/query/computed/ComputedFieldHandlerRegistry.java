package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import querydsl.query.QueryCondition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for computed field handlers.
 *
 * <p>Auto-discovers every {@link TypedComputedFieldHandler} bean in the Spring context and
 * indexes them by {@code (entityClass, fieldName)} for O(1) lookup at query time.
 *
 * <p>Example registration — declaring a handler is automatic, just annotate the class:
 * <pre>
 * &#64;Component
 * public class MyHandler implements TypedComputedFieldHandler&lt;MyEntity, QMyEntity&gt; { ... }
 * </pre>
 *
 * @see TypedComputedFieldHandler
 */
@Component
@Slf4j
public class ComputedFieldHandlerRegistry {

    private final Map<Class<?>, Map<String, TypedComputedFieldHandler<?, ?>>> typedHandlers = new HashMap<>();

    /** Auto-registers every {@link TypedComputedFieldHandler} bean in the Spring context. */
    @Autowired(required = false)
    public void setTypedHandlers(List<TypedComputedFieldHandler<?, ?>> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        handlers.forEach(this::registerTyped);
        log.info("Auto-registered {} computed field handler(s)", handlers.size());
    }

    /** Manual registration entry point (programmatic registration, e.g. in tests). */
    public void registerTyped(TypedComputedFieldHandler<?, ?> handler) {
        if (handler == null) {
            return;
        }
        Class<?> entityClass = handler.getEntityClass();
        String fieldName = handler.getFieldName();
        typedHandlers.computeIfAbsent(entityClass, k -> new HashMap<>()).put(fieldName, handler);
        log.debug("Registered handler {} for {}.{}",
                handler.getClass().getSimpleName(), entityClass.getSimpleName(), fieldName);
    }

    /**
     * Returns a predicate for the requested computed field, or {@code null} if no handler is
     * registered for the {@code (entityClass, fieldName)} pair.
     *
     * @param qEntity     QueryDSL Q-entity root
     * @param condition   the incoming query condition
     * @param entityClass the JPA entity class
     * @return the computed-field predicate, or {@code null} if this is not a computed field
     */
    public Predicate buildPredicate(Object qEntity, QueryCondition condition, Class<?> entityClass) {
        Map<String, TypedComputedFieldHandler<?, ?>> entityHandlers = typedHandlers.get(entityClass);
        if (entityHandlers == null) {
            return null;
        }
        TypedComputedFieldHandler<?, ?> handler = entityHandlers.get(condition.getField());
        if (handler == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            TypedComputedFieldHandler<Object, Object> typed =
                    (TypedComputedFieldHandler<Object, Object>) handler;
            return typed.buildPredicate(qEntity, condition);
        } catch (ClassCastException e) {
            log.warn("Type mismatch invoking handler {} for {}.{}: {}",
                    handler.getClass().getSimpleName(), entityClass.getSimpleName(),
                    condition.getField(), e.getMessage());
            return null;
        }
    }
}
