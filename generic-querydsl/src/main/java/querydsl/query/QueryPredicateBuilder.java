package querydsl.query;


import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import querydsl.exception.QueryException;
import querydsl.query.computed.ComputedFieldHandlerRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Builds QueryDSL predicates from {@link QueryRequest} objects.
 *
 * <p>Spring service. Holds a static reference to the singleton instance so that
 * {@link querydsl.repository.GenericQueryRepository}'s interface default methods can
 * reach it without DI (interfaces can't be {@code @Autowired}).
 *
 * <p>The static {@code instance} field is {@code volatile} so that a thread reading it
 * from outside the writer thread (Spring init) sees either {@code null} or a fully
 * constructed bean — no torn read possible. The Spring-managed write happens in
 * {@link #registerInstance()} during context startup; any HTTP request thread therefore
 * sees a non-null instance.
 */
@Slf4j
@Service
public class QueryPredicateBuilder {

    private final ComputedFieldHandlerRegistry handlerRegistry;

    private static volatile QueryPredicateBuilder instance;

    @Autowired
    public QueryPredicateBuilder(ComputedFieldHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @PostConstruct
    void registerInstance() {
        QueryPredicateBuilder.instance = this;
    }

    /**
     * Returns the Spring-managed singleton.
     *
     * @throws IllegalStateException if Spring has not yet initialised the bean
     */
    public static QueryPredicateBuilder getInstance() {
        QueryPredicateBuilder local = instance;
        if (local == null) {
            throw new IllegalStateException(
                    "QueryPredicateBuilder has not been initialised. Ensure the Spring "
                            + "context is loaded (auto-configured by generic-querydsl, or "
                            + "imported via @Import(GenericQuerydslAutoConfiguration.class)).");
        }
        return local;
    }
    
    // Cache for Q-entity instances (thread-safe, reduces reflection overhead)
    private static final Map<Class<?>, Object> Q_ENTITY_CACHE = new ConcurrentHashMap<>(32);
    
    // Cache for field lookups (thread-safe, reduces repeated reflection calls)
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>(128);
    
    // Maximum number of conditions allowed per query (prevents resource exhaustion)
    private static final int MAX_CONDITIONS = 50;
    
    // Maximum depth of nested field paths (e.g., "role.name" = depth 2)
    private static final int MAX_FIELD_PATH_DEPTH = 5;
    
    // Maximum number of values allowed in IN operations (prevents memory exhaustion)
    private static final int MAX_IN_VALUES = 1000;
    
    // Field name validation pattern: alphanumeric, underscore, and dot only
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");
    
    // Date-time format constants
    private static final int ISO_DATE_TIME_LENGTH = 19; // "2025-11-01T00:00:00"
    private static final int ISO_DATE_TIME_T_INDEX = 10;
    private static final int ISO_DATE_LENGTH = 10; // "2025-11-01"
    private static final int MILLISECONDS_LENGTH = 3;
    
    /**
     * Builds a QueryDSL predicate from a QueryRequest with validation.
     * 
     * All conditions are combined using AND operation. If any condition cannot be built,
     * it is skipped (logged) and other conditions continue to be processed.
     * 
     * @param <T> The entity type
     * @param queryRequest The query request containing conditions and optional sort fields
     * @param entityClass The entity class to build predicates for
     * @return QueryDSL predicate, or null if no valid conditions could be built
     * @throws IllegalArgumentException if query request validation fails
     */
    public <T> Predicate buildPredicate(QueryRequest queryRequest, Class<T> entityClass) {
        if (queryRequest == null || queryRequest.getConditions() == null || queryRequest.getConditions().isEmpty()) {
            return null;
        }
        
        // Validate query request (defensive check - conditions list could be modified)
        List<QueryCondition> conditions = queryRequest.getConditions();

        validateQueryRequest(queryRequest);
        
        // Use BooleanBuilder to combine predicates with AND operation
        // BooleanBuilder automatically handles null predicates and provides cleaner code
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        
        for (QueryCondition condition : conditions) {
            Predicate predicate = this.buildConditionPredicate(condition, entityClass);
            if (predicate != null) {
                booleanBuilder.and(predicate);
            }
        }
        
        // BooleanBuilder returns null if no conditions were added, which is the desired behavior
        return booleanBuilder.getValue();
    }
    
    /**
     * Validates query request to prevent resource exhaustion attacks and injection vulnerabilities.
     * 
     * Validates:
     * - Number of conditions (max limit)
     * - Field name patterns (alphanumeric, underscore, dot only)
     * - Field path depth (max nesting level)
     * - IN operation value count (max limit)
     * - BETWEEN operation values (both required)
     * - Sort field patterns and depths
     * 
     * @param queryRequest The query request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private static void validateQueryRequest(QueryRequest queryRequest) {
        if (queryRequest == null) {
            throw new IllegalArgumentException("QueryRequest cannot be null");
        }
        
        List<QueryCondition> conditions = queryRequest.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            // Already handled in buildPredicate, but validate here for consistency
            return;
        }
        
        if (conditions.size() > MAX_CONDITIONS) {
            throw new IllegalArgumentException(
                String.format("Too many conditions. Maximum allowed: %d, provided: %d", 
                    MAX_CONDITIONS, conditions.size()));
        }
        
        // Validate field path depths and field names
        for (QueryCondition condition : conditions) {
            if (condition.getField() != null) {
                String field = condition.getField();
                
                // Validate field name pattern (security: prevent injection)
                if (!FIELD_NAME_PATTERN.matcher(field).matches()) {
                    throw new IllegalArgumentException(
                        String.format("Invalid field name: '%s'. Field names can only contain alphanumeric characters, underscores, and dots", field));
                }
                
                // Validate field path depth
                int depth = field.split("\\.").length;
                if (depth > MAX_FIELD_PATH_DEPTH) {
                    throw new IllegalArgumentException(
                        String.format("Field path too deep: '%s'. Maximum depth: %d", 
                            field, MAX_FIELD_PATH_DEPTH));
                }
                
                // Validate IN operation values size
                if (condition.getOperation() == QueryOperation.IN ||
                    condition.getOperation() == QueryOperation.NOT_IN) {
                    if (condition.getValues() != null && condition.getValues().size() > MAX_IN_VALUES) {
                        throw new IllegalArgumentException(
                            String.format("Too many values in IN operation. Maximum allowed: %d, provided: %d", 
                                MAX_IN_VALUES, condition.getValues().size()));
                    }
                }
                
                // Validate BETWEEN operation
                if (condition.getOperation() == QueryOperation.BETWEEN ||
                    condition.getOperation() == QueryOperation.NOT_BETWEEN) {
                    if (condition.getStartValue() == null || condition.getEndValue() == null) {
                        throw new IllegalArgumentException(
                            "BETWEEN operation requires both startValue and endValue to be provided");
                    }
                }
            }
        }
        
        // Validate sort fields if present
        List<SortField> sortFields = queryRequest.getSortFields();
        if (sortFields != null && !sortFields.isEmpty()) {
            for (SortField sortField : sortFields) {
                if (sortField == null) {
                    continue; // Skip null sort fields
                }
                
                String field = sortField.getField();
                if (field == null || field.trim().isEmpty()) {
                    continue; // Skip empty sort fields
                }
                
                // Validate field name pattern (security: prevent injection)
                if (!FIELD_NAME_PATTERN.matcher(field).matches()) {
                    throw new IllegalArgumentException(
                        String.format("Invalid sort field name: '%s'. Field names can only contain alphanumeric characters, underscores, and dots", field));
                }
                
                // Validate field path depth
                int depth = field.split("\\.").length;
                if (depth > MAX_FIELD_PATH_DEPTH) {
                    throw new IllegalArgumentException(
                        String.format("Sort field path too deep: '%s'. Maximum depth: %d", 
                            field, MAX_FIELD_PATH_DEPTH));
                }
            }
        }
    }
    
    /**
     * Builds a predicate for a single query condition with caching.
     * 
     * First checks for computed fields (handled by registry), then falls back to
     * regular field predicates. Uses default operation EQUALS if not specified.
     * 
     * @param <T> The entity type
     * @param condition The query condition to build predicate for
     * @param entityClass The entity class
     * @return Predicate for the condition, or null if condition is invalid
     * @throws InvalidFieldException if field cannot be found or accessed
     */
    private <T> Predicate buildConditionPredicate(QueryCondition condition, Class<T> entityClass) {
        if (condition == null || !StringUtils.hasText(condition.getField())) {
            return null;
        }
        
        // Use default operation EQUALS if not specified (don't modify original condition)
        QueryOperation operation = condition.getOperation();
        if (operation == null) {
            operation = QueryOperation.EQUALS;
            log.debug("No operation specified for field '{}', defaulting to EQUALS", condition.getField());
        }
        
        try {
            // First, check if this is a computed field (before loading Q-entity)
            // Computed fields don't require the Q-entity to exist in the database schema
            if (handlerRegistry != null) {
                // Try to get Q-entity for computed field handlers (they need it to build predicates)
                Object qEntity = null;
                try {
                    qEntity = getOrLoadQEntity(entityClass);
                } catch (Exception e) {
                    log.warn("Could not load Q-entity for computed field check: {}", e.getMessage());
                    // Continue anyway - handler might still work
                }
                
                if (qEntity != null) {
                    Predicate computedPredicate = handlerRegistry.buildPredicate(qEntity, condition, entityClass);
                    if (computedPredicate != null) {
                        log.debug("Built computed field predicate for field '{}' on entity {}", 
                            condition.getField(), entityClass.getSimpleName());
                        return computedPredicate;
                    }
                }
            }
            
            // If not a computed field, get Q-entity and build regular field predicate
            Object qEntity = getOrLoadQEntity(entityClass);
            // Create a defensive copy of condition with default operation if needed
            QueryCondition conditionToUse = getQueryCondition(condition, operation);
            return buildFieldPredicate(qEntity, conditionToUse, entityClass);
            
        } catch (InvalidFieldException | QueryException | IllegalArgumentException e) {
            // Bubble up domain exceptions so the global handler can produce the right 4xx.
            throw e;
        } catch (Exception e) {
            log.error("Could not build predicate for field: {} - {}", condition.getField(), e.getMessage(), e);
            throw new InvalidFieldException(condition.getField(), entityClass);
        }
    }

    private static QueryCondition getQueryCondition(QueryCondition condition, QueryOperation operation) {
        QueryCondition conditionToUse = condition;
        if (operation != condition.getOperation()) {
            // Create a copy with the default operation (don't modify original)
            conditionToUse = new QueryCondition();
            conditionToUse.setField(condition.getField());
            conditionToUse.setOperation(operation);
            conditionToUse.setValue(condition.getValue());
            conditionToUse.setValues(condition.getValues());
            conditionToUse.setStartValue(condition.getStartValue());
            conditionToUse.setEndValue(condition.getEndValue());
        }
        return conditionToUse;
    }

    /**
     * Gets or loads Q-entity from cache to avoid repeated reflection operations.
     * 
     * Uses thread-safe ConcurrentHashMap for caching. Each Q-entity is loaded once
     * and reused for all subsequent queries.
     * 
     * @param entityClass The entity class to get Q-entity for
     * @return The Q-entity instance (e.g., QUser.user)
     * @throws RuntimeException if Q-entity cannot be loaded
     */
    private static Object getOrLoadQEntity(Class<?> entityClass) {
        return Q_ENTITY_CACHE.computeIfAbsent(entityClass, QueryPredicateBuilder::loadQEntity);
    }
    
    /**
     * Loads Q-entity using reflection (cached after first load).
     * 
     * Attempts to load the Q-entity field in camelCase format first (e.g., QUser.user),
     * then falls back to all-lowercase format (e.g., QUser.user → field name "user")
     * for backward compatibility with non-standard QueryDSL configurations.
     * 
     * @param entityClass The entity class to load Q-entity for
     * @return The Q-entity instance
     * @throws RuntimeException if Q-entity cannot be loaded (class not found, field not found, etc.)
     */
    private static Object loadQEntity(Class<?> entityClass) {
        try {
            String qClassName = entityClass.getSimpleName();
            if (!qClassName.startsWith("Q")) {
                qClassName = "Q" + qClassName;
            }
            
            Class<?> qClass = Class.forName(entityClass.getPackage().getName() + "." + qClassName);
            
            // QueryDSL generates field names in camelCase: QUser -> user
            // Convert "User" to "user" (first letter lowercase)
            String entitySimpleName = entityClass.getSimpleName();
            String fieldName = entitySimpleName.substring(0, 1).toLowerCase() + entitySimpleName.substring(1);
            
            Field field = qClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
            
        } catch (NoSuchFieldException e) {
            // Try alternative field name format (all lowercase) for backward compatibility
            try {
                String qClassName = entityClass.getSimpleName();
                if (!qClassName.startsWith("Q")) {
                    qClassName = "Q" + qClassName;
                }
                Class<?> qClass = Class.forName(entityClass.getPackage().getName() + "." + qClassName);
                String fieldName = entityClass.getSimpleName().toLowerCase();
                Field field = qClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(null);
            } catch (Exception e2) {
                log.error("Failed to load Q-entity for: {} (tried both camelCase and lowercase)", entityClass.getName(), e);
                throw new RuntimeException("Failed to load Q-entity for: " + entityClass.getName(), e);
            }
        } catch (Exception e) {
            log.error("Failed to load Q-entity for: {}", entityClass.getName(), e);
            throw new RuntimeException("Failed to load Q-entity for: " + entityClass.getName(), e);
        }
    }
    
    /**
     * Builds a predicate for a specific field and condition.
     * 
     * Note: Computed fields should be checked before calling this method.
     * This method handles regular database fields only.
     * 
     * @param qEntity The QueryDSL Q-entity root (e.g., QUser.user)
     * @param condition The query condition with field path and operation
     * @param entityClass The entity class for error messages
     * @return Predicate for the field condition
     * @throws InvalidFieldException if field cannot be found or accessed
     */
    private Predicate buildFieldPredicate(Object qEntity, QueryCondition condition, Class<?> entityClass) {
        try {
            // Navigate to the field using dot notation (e.g., "role.name")
            Object fieldPath = navigateToField(qEntity, condition.getField());
            
            if (fieldPath == null) {
                log.error("Field not found: {} in entity {}", condition.getField(), entityClass.getSimpleName());
                throw new InvalidFieldException(condition.getField(), entityClass);
            }
            
            return buildOperationPredicate(fieldPath, condition);
            
        } catch (InvalidFieldException | QueryException | IllegalArgumentException e) {
            // Bubble up domain exceptions so the global handler can produce the right 4xx.
            throw e;
        } catch (Exception e) {
            log.error("Error building predicate for field: {} - {}", condition.getField(), e.getMessage());
            throw new InvalidFieldException(condition.getField(), entityClass);
        }
    }


    /**
     * Navigates to a field using dot notation with caching.
     * 
     * Handles nested field paths (e.g., "role.name") and automatically applies
     * QueryDSL's any() method when traversing collection relationships.
     * 
     * Uses field caching to avoid repeated reflection operations.
     * 
     * @param qEntity The QueryDSL Q-entity root to start navigation from
     * @param fieldPath The field path (e.g., "role.name", "role.permissions.name")
     * @return The field path expression for building predicates
     * @throws IllegalArgumentException if the field path cannot be resolved
     */
    private static Object navigateToField(Object qEntity, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Object current = qEntity;
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            try {
                // Make current effectively final for lambda
                final Object currentObj = current;
                
                // Use cached field lookup to avoid repeated reflection
                String cacheKey = currentObj.getClass().getName() + "." + part;
                Field field = FIELD_CACHE.computeIfAbsent(cacheKey, key -> {
                    try {
                        Field f = currentObj.getClass().getDeclaredField(part);
                        f.setAccessible(true);
                        return f;
                    } catch (NoSuchFieldException e) {
                        log.error("Field '{}' not found in class: {}", part, currentObj.getClass().getName());
                        throw new IllegalArgumentException(
                            String.format("Field '%s' does not exist in class '%s'", 
                                part, currentObj.getClass().getSimpleName()), e);
                    } catch (Exception e) {
                        log.error("Could not load field: {}", part);
                        throw new RuntimeException("Failed to load field: " + part, e);
                    }
                });
                
                current = field.get(current);
                
                // If the field is a collection path and we have more parts to navigate, use any()
                if (isCollectionPath(current) && i < parts.length - 1) {
                    try {
                        // Call any() method on the collection path
                        Method anyMethod = current.getClass().getMethod("any");
                        current = anyMethod.invoke(current);
                        log.debug("Applied any() to collection path: {}", part);
                    } catch (NoSuchMethodException e) {
                        log.warn("Collection path '{}' does not have any() method, trying to continue", part);
                        // Continue without any() - might work for some collection types
                    } catch (Exception e) {
                        log.error("Could not invoke any() on collection path '{}': {}", part, e.getMessage());
                        throw new IllegalArgumentException(
                            String.format("Error accessing collection field '%s' in path '%s': %s", 
                                part, fieldPath, e.getMessage()), e);
                    }
                }
                
            } catch (IllegalArgumentException e) {
                // Re-throw as-is
                throw e;
            } catch (Exception e) {
                log.error("Could not navigate to field: {} in path: {}", part, fieldPath);
                throw new IllegalArgumentException(
                    String.format("Error accessing field '%s' in path '%s': %s", 
                        part, fieldPath, e.getMessage()), e);
            }
        }
        
        return current;
    }
    
    /**
     * Checks if the given object is a QueryDSL collection path.
     * 
     * Collection paths require the any() method to be called when navigating
     * to nested fields (e.g., "role.permissions.name" where permissions is a collection).
     * 
     * @param obj The object to check
     * @return true if the object is a QueryDSL collection path (CollectionPathBase, SetPath, ListPath, ArrayPath)
     */
    private static boolean isCollectionPath(Object obj) {
        if (obj == null) {
            return false;
        }
        
        // Check if it's a CollectionPathBase (QueryDSL's base class for collection paths)
        if (obj instanceof CollectionPathBase) {
            return true;
        }
        
        // Check the class name for QueryDSL collection path types
        String className = obj.getClass().getName();
        return className.contains("CollectionPath") || 
               className.contains("SetPath") || 
               className.contains("ListPath") ||
               className.contains("ArrayPath");
    }
    
    /**
     * Builds the actual operation predicate based on the condition.
     * 
     * Converts values to appropriate types (enums, dates, numbers) and validates
     * operation-specific requirements (e.g., BETWEEN requires both start and end values).
     * 
     * @param fieldPath The QueryDSL field path expression
     * @param condition The query condition with operation and values
     * @return Predicate for the operation, or null if operation is not supported
     * @throws IllegalArgumentException if operation validation fails or type conversion fails
     */
    private static Predicate buildOperationPredicate(Object fieldPath, QueryCondition condition) {
        QueryOperation operation = condition.getOperation();
        
        if (operation == null) {
            return null;
        }
        
        // Convert values to appropriate types based on field path
        Object convertedValue = convertValueToFieldType(fieldPath, condition.getValue());
        Object convertedStartValue = convertValueToFieldType(fieldPath, condition.getStartValue());
        Object convertedEndValue = convertValueToFieldType(fieldPath, condition.getEndValue());
        List<Object> convertedValues = condition.getValues() != null ? 
            condition.getValues().stream()
                .map(value -> convertValueToFieldType(fieldPath, value))
                .toList() : null;
        
        // Validate BETWEEN operation values
        if (operation == QueryOperation.BETWEEN || operation == QueryOperation.NOT_BETWEEN) {
            if (convertedStartValue == null || convertedEndValue == null) {
                throw new IllegalArgumentException("BETWEEN operation requires both startValue and endValue to be non-null after conversion");
            }
            // Validate that startValue <= endValue for comparable types
            if (convertedStartValue instanceof Comparable && convertedEndValue instanceof Comparable) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Comparable start = (Comparable) convertedStartValue;
                @SuppressWarnings({"unchecked", "rawtypes"})
                Comparable end = (Comparable) convertedEndValue;
                if (start.compareTo(end) > 0) {
                    throw new IllegalArgumentException(
                        String.format("BETWEEN operation: startValue (%s) must be less than or equal to endValue (%s)", 
                            convertedStartValue, convertedEndValue));
                }
            }
        }
        
        switch (operation) {
            case EQUALS:
                return buildEqualsPredicate(fieldPath, convertedValue);
            case NOT_EQUALS:
                return buildNotEqualsPredicate(fieldPath, convertedValue);
            case CONTAINS:
                return buildContainsPredicate(fieldPath, convertedValue);
            case NOT_CONTAINS:
                return buildNotContainsPredicate(fieldPath, convertedValue);
            case CONTAINS_IGNORE_CASE:
                return buildContainsIgnoreCasePredicate(fieldPath, convertedValue);
            case NOT_CONTAINS_IGNORE_CASE:
                return buildNotContainsIgnoreCasePredicate(fieldPath, convertedValue);
            case STARTS_WITH:
                return buildStartsWithPredicate(fieldPath, convertedValue);
            case NOT_STARTS_WITH:
                return buildStartsWithPredicate(fieldPath, convertedValue).not();
            case STARTS_WITH_IGNORE_CASE:
                return buildStartsWithIgnoreCasePredicate(fieldPath, convertedValue);
            case NOT_STARTS_WITH_IGNORE_CASE:
                return buildStartsWithIgnoreCasePredicate(fieldPath, convertedValue).not();
            case ENDS_WITH:
                return buildEndsWithPredicate(fieldPath, convertedValue);
            case NOT_ENDS_WITH:
                return buildEndsWithPredicate(fieldPath, convertedValue).not();
            case ENDS_WITH_IGNORE_CASE:
                return buildEndsWithIgnoreCasePredicate(fieldPath, convertedValue);
            case NOT_ENDS_WITH_IGNORE_CASE:
                return buildEndsWithIgnoreCasePredicate(fieldPath, convertedValue).not();
            case BETWEEN:
                return buildBetweenPredicate(fieldPath, convertedStartValue, convertedEndValue);
            case NOT_BETWEEN:
                return buildNotBetweenPredicate(fieldPath, convertedStartValue, convertedEndValue);
            case GREATER_THAN:
                return buildGreaterThanPredicate(fieldPath, convertedValue);
            case GREATER_THAN_OR_EQUAL:
                return buildGreaterThanOrEqualPredicate(fieldPath, convertedValue);
            case LESS_THAN:
                return buildLessThanPredicate(fieldPath, convertedValue);
            case LESS_THAN_OR_EQUAL:
                return buildLessThanOrEqualPredicate(fieldPath, convertedValue);
            case IN:
                return buildInPredicate(fieldPath, convertedValues);
            case NOT_IN:
                return buildNotInPredicate(fieldPath, convertedValues);
            case IS_NULL:
                return buildIsNullPredicate(fieldPath);
            case IS_NOT_NULL:
                return buildIsNotNullPredicate(fieldPath);
            case IS_TRUE:
                return buildIsTruePredicate(fieldPath);
            case IS_FALSE:
                return buildIsFalsePredicate(fieldPath);
            default:
                throw new QueryException("Unsupported query operation: " + operation);
        }
    }

    /**
     * Throws a QueryException explaining why a given operation cannot be applied to the field type.
     * Centralises the error message so every helper produces a consistent shape.
     */
    private static QueryException unsupportedTypeFor(QueryOperation operation, Object fieldPath, String expectedType) {
        String actualType = (fieldPath != null) ? fieldPath.getClass().getSimpleName() : "null";
        return new QueryException(
                String.format("Operation %s requires a %s field; got %s. Check the QueryOperation/field-type matrix in docs/USER_GUIDE.md.",
                        operation, expectedType, actualType));
    }
    
    /**
     * Converts a value to the appropriate type based on the field path type.
     * 
     * Handles type conversions for:
     * - Enums (from String to enum value)
     * - Date/time types (LocalDateTime, LocalDate from ISO-8601 strings)
     * - Numeric types (Long, Integer, Double, Float from String)
     * - Boolean (from String)
     * 
     * @param fieldPath The QueryDSL field path to determine target type
     * @param value The value to convert (can be String, number, enum, etc.)
     * @return Converted value of the appropriate type, or original value if no conversion needed
     * @throws IllegalArgumentException if conversion fails (e.g., invalid date format, invalid enum value)
     */
    private static Object convertValueToFieldType(Object fieldPath, Object value) {
        if (value == null) {
            return null;
        }
        
        try {
            // Get the field type from the QueryDSL path
            if (fieldPath instanceof SimpleExpression) {
                SimpleExpression<?> expr = (SimpleExpression<?>) fieldPath;
                Class<?> fieldType = expr.getType();
                
                // Handle enum types
                if (fieldType.isEnum() && value instanceof String) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Class<? extends Enum> enumClass = (Class<? extends Enum>) fieldType;
                    return Enum.valueOf((Class) enumClass, (String) value);
                }
                
                // Handle date/time types
                if (value instanceof String) {
                    String stringValue = (String) value;
                    
                    if (fieldType == LocalDateTime.class) {
                        return parseLocalDateTime(stringValue);
                    }
                    if (fieldType == LocalDate.class) {
                        return parseLocalDate(stringValue);
                    }
                }
                
                // Handle other type conversions
                if (fieldType == Long.class && value instanceof String) {
                    return Long.valueOf((String) value);
                }
                if (fieldType == Integer.class && value instanceof String) {
                    return Integer.valueOf((String) value);
                }
                if (fieldType == Boolean.class && value instanceof String) {
                    return Boolean.valueOf((String) value);
                }
                if (fieldType == Double.class && value instanceof String) {
                    return Double.valueOf((String) value);
                }
                if (fieldType == Float.class && value instanceof String) {
                    return Float.valueOf((String) value);
                }
            }
            
            // Return the original value if no conversion is needed
            return value;
            
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException as-is (e.g., invalid date format)
            throw e;
        } catch (Exception e) {
            log.error("Could not convert value {} to field type: {}", value, e.getMessage(), e);
            throw new IllegalArgumentException(
                String.format("Type conversion failed for value '%s': %s", value, e.getMessage()), e);
        }
    }
    
    /**
     * Parses a string to LocalDateTime.
     *
     * <p><strong>Convention:</strong> the engine stores and compares {@code LocalDateTime}
     * columns as <em>UTC</em>. Input with a timezone (e.g. {@code Z}, {@code +03:00})
     * is converted to UTC before being treated as a wall-clock {@code LocalDateTime}.
     * Input without a timezone is interpreted as already-UTC.
     *
     * <p>Phase 3 fix 3.6: the previous implementation silently stripped timezone
     * indicators, which meant {@code "2025-11-01T00:00:00+03:00"} was parsed as
     * {@code 2025-11-01T00:00:00} server-local time — a 3-hour data-correctness skew
     * depending on JVM {@code user.timezone}.
     *
     * <p>Supported input formats:
     * <ul>
     *   <li>ISO-8601 with time: {@code "2025-11-01T00:00:00"} (assumed UTC)</li>
     *   <li>ISO-8601 with milliseconds: {@code "2025-11-01T00:00:00.000"} (assumed UTC)</li>
     *   <li>ISO-8601 with UTC marker: {@code "2025-11-01T00:00:00Z"}</li>
     *   <li>ISO-8601 with offset: {@code "2025-11-01T00:00:00+03:00"} — converted to UTC</li>
     *   <li>Date only (start of day UTC): {@code "2025-11-01"}</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the input cannot be parsed
     */
    private static LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (isDateOnlyFormat(trimmed)) {
                return LocalDate.parse(trimmed).atStartOfDay();
            }
            if (hasTimezoneIndicator(trimmed)) {
                return OffsetDateTime.parse(trimmed)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            }
            if (trimmed.contains("T")) {
                return parseDateTimeWithTime(trimmed);
            }
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.error("Could not parse LocalDateTime from string: {}", value);
            throw new IllegalArgumentException("Invalid date/time format: " + value
                    + ". Expected ISO-8601 (e.g. 2025-11-01T00:00:00, 2025-11-01T00:00:00Z, "
                    + "2025-11-01T00:00:00+03:00, or 2025-11-01).", e);
        }
    }

    /**
     * Returns {@code true} if the value carries a timezone indicator after the
     * date portion (Z, +HH:MM, -HH:MM). The leading {@code yyyy-MM-dd} also
     * contains dashes, so we only look from {@code ISO_DATE_TIME_T_INDEX} onward.
     */
    private static boolean hasTimezoneIndicator(String value) {
        if (value.length() <= ISO_DATE_TIME_T_INDEX) {
            return false;
        }
        String afterDate = value.substring(ISO_DATE_TIME_T_INDEX);
        if (afterDate.indexOf('Z') >= 0) {
            return true;
        }
        if (afterDate.indexOf('+') >= 0) {
            return true;
        }
        // Negative offset like "...T00:00:00-05:00". Skip the 'T' and look for '-'
        // immediately followed by a digit (rules out the date-internal dashes which
        // are already past at this point).
        for (int i = 1; i < afterDate.length() - 1; i++) {
            if (afterDate.charAt(i) == '-' && Character.isDigit(afterDate.charAt(i + 1))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if the string is in date-only format (yyyy-MM-dd).
     * 
     * @param value The string to check
     * @return true if the string matches date-only format
     */
    private static boolean isDateOnlyFormat(String value) {
        return value.length() == ISO_DATE_LENGTH && value.matches("\\d{4}-\\d{2}-\\d{2}");
    }
    
    /**
     * Parses a date-time string that contains time component (contains 'T').
     * Handles both standard format and format with milliseconds.
     * 
     * @param value The date-time string (timezone already removed)
     * @return Parsed LocalDateTime
     */
    private static LocalDateTime parseDateTimeWithTime(String value) {
        if (value.contains(".")) {
            return parseDateTimeWithMilliseconds(value);
        } else {
            // Standard ISO-8601 format: "2025-11-01T00:00:00"
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
    
    /**
     * Parses a date-time string with milliseconds component.
     * Normalizes milliseconds to exactly 3 digits.
     * 
     * @param value The date-time string with milliseconds
     * @return Parsed LocalDateTime
     */
    private static LocalDateTime parseDateTimeWithMilliseconds(String value) {
        int dotIndex = value.indexOf('.');
        String dateTimePart = value.substring(0, dotIndex);
        String fractionPart = value.substring(dotIndex + 1);
        
        // Limit to 9 digits for nanoseconds, normalize to 3 for milliseconds
        if (fractionPart.length() > 9) {
            fractionPart = fractionPart.substring(0, 9);
        }
        
        // Normalize to exactly 3 digits (milliseconds)
        String normalizedFraction = normalizeMilliseconds(fractionPart);
        String normalizedDateTime = dateTimePart + "." + normalizedFraction;
        
        return LocalDateTime.parse(normalizedDateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
    }
    
    /**
     * Normalizes milliseconds string to exactly 3 digits.
     * Pads with zeros if shorter, truncates if longer.
     * 
     * @param fraction The fraction part (milliseconds/nanoseconds)
     * @return Normalized 3-digit milliseconds string
     */
    private static String normalizeMilliseconds(String fraction) {
        StringBuilder builder = new StringBuilder(fraction);
        while (builder.length() < MILLISECONDS_LENGTH) {
            builder.append('0');
        }
        if (builder.length() > MILLISECONDS_LENGTH) {
            builder.setLength(MILLISECONDS_LENGTH);
        }
        return builder.toString();
    }
    
    /**
     * Parses a string to LocalDate, supporting ISO-8601 formats.
     * 
     * Supported formats:
     * - ISO-8601 date: "2025-11-01"
     * - ISO-8601 date-time (extracts date part only): "2025-11-01T00:00:00"
     * 
     * If a date-time string is provided, only the date part is extracted and parsed.
     * 
     * @param value The string value to parse
     * @return Parsed LocalDate, or null if input is null or empty
     * @throws IllegalArgumentException if the string cannot be parsed as a valid date
     */
    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = value.trim();
        
        try {
            // If it's a date-time string, extract just the date part
            if (trimmed.contains("T")) {
                trimmed = trimmed.substring(0, trimmed.indexOf('T'));
            }
            
            // Parse ISO-8601 date format: "2025-11-01"
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            
        } catch (DateTimeParseException e) {
            log.error("Could not parse LocalDate from string: {}", trimmed);
            throw new IllegalArgumentException("Invalid date format: " + trimmed + 
                ". Expected format: yyyy-MM-dd", e);
        }
    }
    
    // ========================================================================
    // Helper methods for building specific operation predicates
    // ========================================================================
    
    /**
     * Builds an EQUALS predicate.
     * 
     * @param fieldPath The field path expression
     * @param value The value to compare against
     * @return Predicate for EQUALS operation, or null if fieldPath is not a SimpleExpression
     */
    @SuppressWarnings("unchecked")
    private static Predicate buildEqualsPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof SimpleExpression) {
            return ((SimpleExpression<Object>) fieldPath).eq(value);
        }
        throw unsupportedTypeFor(QueryOperation.EQUALS, fieldPath, "SimpleExpression");
    }

    @SuppressWarnings("unchecked")
    private static Predicate buildNotEqualsPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof SimpleExpression) {
            return ((SimpleExpression<Object>) fieldPath).ne(value);
        }
        throw unsupportedTypeFor(QueryOperation.NOT_EQUALS, fieldPath, "SimpleExpression");
    }
    
    /**
     * Builds a CONTAINS predicate (case-sensitive substring match).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The substring to search for
     * @return Predicate for CONTAINS operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildContainsPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).contains(value.toString());
        }
        throw unsupportedTypeFor(QueryOperation.CONTAINS, fieldPath, "StringExpression");
    }

    private static Predicate buildNotContainsPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).contains(value.toString()).not();
        }
        throw unsupportedTypeFor(QueryOperation.NOT_CONTAINS, fieldPath, "StringExpression");
    }

    private static Predicate buildContainsIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).containsIgnoreCase(value.toString());
        }
        throw unsupportedTypeFor(QueryOperation.CONTAINS_IGNORE_CASE, fieldPath, "StringExpression");
    }

    private static Predicate buildNotContainsIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).containsIgnoreCase(value.toString()).not();
        }
        throw unsupportedTypeFor(QueryOperation.NOT_CONTAINS_IGNORE_CASE, fieldPath, "StringExpression");
    }

    private static Predicate buildStartsWithPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).startsWith(value.toString());
        }
        throw unsupportedTypeFor(QueryOperation.STARTS_WITH, fieldPath, "StringExpression");
    }

    private static Predicate buildStartsWithIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).startsWithIgnoreCase(value.toString());
        }
        throw unsupportedTypeFor(QueryOperation.STARTS_WITH_IGNORE_CASE, fieldPath, "StringExpression");
    }

    private static Predicate buildEndsWithPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).endsWith(value.toString());
        }
        throw unsupportedTypeFor(QueryOperation.ENDS_WITH, fieldPath, "StringExpression");
    }

    private static Predicate buildEndsWithIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).endsWithIgnoreCase(value.toString());
        }
        throw unsupportedTypeFor(QueryOperation.ENDS_WITH_IGNORE_CASE, fieldPath, "StringExpression");
    }
    
    /**
     * Builds a BETWEEN predicate (inclusive range).
     * 
     * @param fieldPath The field path expression (must be ComparableExpression)
     * @param startValue The start value (inclusive)
     * @param endValue The end value (inclusive)
     * @return Predicate for BETWEEN operation, or null if fieldPath is not a ComparableExpression
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildBetweenPredicate(Object fieldPath, Object startValue, Object endValue) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.between((Comparable) startValue, (Comparable) endValue);
        }
        throw unsupportedTypeFor(QueryOperation.BETWEEN, fieldPath, "ComparableExpression");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildNotBetweenPredicate(Object fieldPath, Object startValue, Object endValue) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.between((Comparable) startValue, (Comparable) endValue).not();
        }
        throw unsupportedTypeFor(QueryOperation.NOT_BETWEEN, fieldPath, "ComparableExpression");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildGreaterThanPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.gt((Comparable) value);
        }
        throw unsupportedTypeFor(QueryOperation.GREATER_THAN, fieldPath, "ComparableExpression");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildGreaterThanOrEqualPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.goe((Comparable) value);
        }
        throw unsupportedTypeFor(QueryOperation.GREATER_THAN_OR_EQUAL, fieldPath, "ComparableExpression");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildLessThanPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.lt((Comparable) value);
        }
        throw unsupportedTypeFor(QueryOperation.LESS_THAN, fieldPath, "ComparableExpression");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildLessThanOrEqualPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.loe((Comparable) value);
        }
        throw unsupportedTypeFor(QueryOperation.LESS_THAN_OR_EQUAL, fieldPath, "ComparableExpression");
    }
    
    /**
     * Builds an IN predicate (value must be in the provided list).
     * 
     * Empty lists result in a predicate that always returns false (no matches).
     * 
     * @param fieldPath The field path expression (must be SimpleExpression)
     * @param values The list of values to match against
     * @return Predicate for IN operation, or null if fieldPath is not a SimpleExpression or values is null/empty
     * @throws IllegalArgumentException if values list exceeds MAX_IN_VALUES
     */
    @SuppressWarnings("unchecked")
    private static Predicate buildInPredicate(Object fieldPath, List<Object> values) {
        if (!(fieldPath instanceof SimpleExpression)) {
            throw unsupportedTypeFor(QueryOperation.IN, fieldPath, "SimpleExpression");
        }
        if (values == null) {
            throw new QueryException("IN operation requires a 'values' list");
        }
        if (values.size() > MAX_IN_VALUES) {
            throw new IllegalArgumentException(
                    String.format("IN operation: too many values. Maximum allowed: %d, provided: %d",
                            MAX_IN_VALUES, values.size()));
        }
        // Empty list deliberately produces `field IN ()` → matches nothing.
        return ((SimpleExpression<Object>) fieldPath).in(values);
    }

    @SuppressWarnings("unchecked")
    private static Predicate buildNotInPredicate(Object fieldPath, List<Object> values) {
        if (!(fieldPath instanceof SimpleExpression)) {
            throw unsupportedTypeFor(QueryOperation.NOT_IN, fieldPath, "SimpleExpression");
        }
        if (values == null) {
            throw new QueryException("NOT_IN operation requires a 'values' list");
        }
        if (values.isEmpty()) {
            // NOT IN of an empty set is universally true; emit an always-true predicate
            // rather than a no-op null (which used to silently AND-skip).
            return Expressions.TRUE.isTrue();
        }
        return ((SimpleExpression<Object>) fieldPath).notIn(values);
    }

    @SuppressWarnings("unchecked")
    private static Predicate buildIsNullPredicate(Object fieldPath) {
        if (fieldPath instanceof SimpleExpression) {
            return ((SimpleExpression<Object>) fieldPath).isNull();
        }
        throw unsupportedTypeFor(QueryOperation.IS_NULL, fieldPath, "SimpleExpression");
    }

    @SuppressWarnings("unchecked")
    private static Predicate buildIsNotNullPredicate(Object fieldPath) {
        if (fieldPath instanceof SimpleExpression) {
            return ((SimpleExpression<Object>) fieldPath).isNotNull();
        }
        throw unsupportedTypeFor(QueryOperation.IS_NOT_NULL, fieldPath, "SimpleExpression");
    }

    private static Predicate buildIsTruePredicate(Object fieldPath) {
        if (fieldPath instanceof BooleanExpression) {
            return ((BooleanExpression) fieldPath).isTrue();
        }
        throw unsupportedTypeFor(QueryOperation.IS_TRUE, fieldPath, "BooleanExpression");
    }

    private static Predicate buildIsFalsePredicate(Object fieldPath) {
        if (fieldPath instanceof BooleanExpression) {
            return ((BooleanExpression) fieldPath).isFalse();
        }
        throw unsupportedTypeFor(QueryOperation.IS_FALSE, fieldPath, "BooleanExpression");
    }
}
