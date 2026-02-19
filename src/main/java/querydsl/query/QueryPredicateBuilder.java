package querydsl.query;


import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.*;
import querydsl.query.computed.ComputedFieldHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service for building QueryDSL predicates from QueryRequest objects
 * Handles various field types and operations with optimized reflection caching
 * 
 * This is a Spring service that also implements ApplicationContextAware to provide
 * static access for interface default methods (e.g., GenericQueryRepository)
 */
@Slf4j
@Service
public class QueryPredicateBuilder implements ApplicationContextAware {
    
    private final ComputedFieldHandlerRegistry handlerRegistry;
    
    private static ApplicationContext applicationContext;
    private static QueryPredicateBuilder instance;
    
    @Autowired
    public QueryPredicateBuilder(ComputedFieldHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        QueryPredicateBuilder.applicationContext = applicationContext;
        // Set static instance for use in interface default methods
        QueryPredicateBuilder.instance = this;
    }
    
    /**
     * Gets the QueryPredicateBuilder service instance.
     * This static method allows interface default methods (e.g., GenericQueryRepository)
     * to access the service without dependency injection.
     * 
     * Uses double-checked locking pattern for thread safety.
     * 
     * @return QueryPredicateBuilder service instance
     * @throws IllegalStateException if ApplicationContext is not initialized
     */
    public static QueryPredicateBuilder getInstance() {
        if (instance == null) {
            synchronized (QueryPredicateBuilder.class) {
                if (instance == null) {
                    if (applicationContext == null) {
                        throw new IllegalStateException(
                            "QueryPredicateBuilder has not been initialized. " +
                            "Ensure Spring context is loaded and QueryPredicateBuilder is a Spring bean.");
                    }
                    instance = applicationContext.getBean(QueryPredicateBuilder.class);
                }
            }
        }
        return instance;
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
            
        } catch (InvalidFieldException e) {
            // Re-throw InvalidFieldException as-is
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
            
        } catch (InvalidFieldException e) {
            // Re-throw InvalidFieldException as-is
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
            case STARTS_WITH_IGNORE_CASE:
                return buildStartsWithIgnoreCasePredicate(fieldPath, convertedValue);
            case ENDS_WITH:
                return buildEndsWithPredicate(fieldPath, convertedValue);
            case ENDS_WITH_IGNORE_CASE:
                return buildEndsWithIgnoreCasePredicate(fieldPath, convertedValue);
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
                log.warn("Unsupported operation: {}", operation);
                return null;
        }
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
     * Parses a string to LocalDateTime, supporting multiple ISO-8601 formats.
     * 
     * Supported formats:
     * - ISO-8601 with time: "2025-11-01T00:00:00"
     * - ISO-8601 with milliseconds: "2025-11-01T00:00:00.000"
     * - ISO-8601 with timezone (Z or offset): "2025-11-01T00:00:00Z", "2025-11-01T00:00:00+03:00"
     * - Date only (assumes start of day 00:00:00): "2025-11-01"
     * 
     * Timezone information is stripped (converted to local time) before parsing.
     * 
     * @param value The string value to parse
     * @return Parsed LocalDateTime, or null if input is null or empty
     * @throws IllegalArgumentException if the string cannot be parsed as a valid date/time
     */
    private static LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = value.trim();
        
        try {
            // Remove timezone indicators if present
            trimmed = removeTimezoneIndicators(trimmed);
            
            // Parse based on format
            if (trimmed.contains("T")) {
                return parseDateTimeWithTime(trimmed);
            }
            
            // Try parsing as date only
            if (isDateOnlyFormat(trimmed)) {
                return LocalDate.parse(trimmed).atStartOfDay();
            }
            
            // Last attempt: try parsing as ISO-8601 date-time directly
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
        } catch (DateTimeParseException e) {
            log.error("Could not parse LocalDateTime from string: {}", value);
            throw new IllegalArgumentException("Invalid date/time format: " + value + 
                ". Expected format: yyyy-MM-ddTHH:mm:ss or yyyy-MM-dd", e);
        }
    }
    
    /**
     * Removes timezone indicators (Z, +HH:MM, -HH:MM) from date-time string.
     * 
     * @param value The date-time string that may contain timezone information
     * @return Date-time string without timezone indicators
     */
    private static String removeTimezoneIndicators(String value) {
        // Remove UTC indicator (Z)
        if (value.contains("Z")) {
            return value.substring(0, value.indexOf('Z'));
        }
        
        // Remove positive timezone offset (+HH:MM)
        if (value.contains("+")) {
            int plusIndex = value.indexOf('+');
            if (plusIndex > ISO_DATE_TIME_T_INDEX) { // After date part
                return value.substring(0, plusIndex);
            }
        }
        
        // Remove negative timezone offset (-HH:MM)
        if (value.length() > ISO_DATE_TIME_LENGTH) {
            int dashIndex = value.indexOf('-', ISO_DATE_TIME_T_INDEX + 1);
            if (dashIndex > 0 && dashIndex < value.length() - 1) {
                char nextChar = value.charAt(dashIndex + 1);
                if (Character.isDigit(nextChar)) {
                    return value.substring(0, dashIndex);
                }
            }
        }
        
        return value;
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
        return null;
    }
    
    /**
     * Builds a NOT_EQUALS predicate.
     * 
     * @param fieldPath The field path expression
     * @param value The value to compare against
     * @return Predicate for NOT_EQUALS operation, or null if fieldPath is not a SimpleExpression
     */
    @SuppressWarnings("unchecked")
    private static Predicate buildNotEqualsPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof SimpleExpression) {
            return ((SimpleExpression<Object>) fieldPath).ne(value);
        }
        return null;
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
        return null;
    }
    
    /**
     * Builds a NOT_CONTAINS predicate (case-sensitive).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The substring to exclude
     * @return Predicate for NOT_CONTAINS operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildNotContainsPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).contains(value.toString()).not();
        }
        return null;
    }
    
    /**
     * Builds a CONTAINS_IGNORE_CASE predicate (case-insensitive substring match).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The substring to search for (case-insensitive)
     * @return Predicate for CONTAINS_IGNORE_CASE operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildContainsIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).containsIgnoreCase(value.toString());
        }
        return null;
    }
    
    /**
     * Builds a NOT_CONTAINS_IGNORE_CASE predicate (case-insensitive).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The substring to exclude (case-insensitive)
     * @return Predicate for NOT_CONTAINS_IGNORE_CASE operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildNotContainsIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).containsIgnoreCase(value.toString()).not();
        }
        return null;
    }
    
    /**
     * Builds a STARTS_WITH predicate (case-sensitive prefix match).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The prefix to match
     * @return Predicate for STARTS_WITH operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildStartsWithPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).startsWith(value.toString());
        }
        return null;
    }
    
    /**
     * Builds a STARTS_WITH_IGNORE_CASE predicate (case-insensitive prefix match).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The prefix to match (case-insensitive)
     * @return Predicate for STARTS_WITH_IGNORE_CASE operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildStartsWithIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).startsWithIgnoreCase(value.toString());
        }
        return null;
    }
    
    /**
     * Builds an ENDS_WITH predicate (case-sensitive suffix match).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The suffix to match
     * @return Predicate for ENDS_WITH operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildEndsWithPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).endsWith(value.toString());
        }
        return null;
    }
    
    /**
     * Builds an ENDS_WITH_IGNORE_CASE predicate (case-insensitive suffix match).
     * 
     * @param fieldPath The field path expression (must be StringExpression)
     * @param value The suffix to match (case-insensitive)
     * @return Predicate for ENDS_WITH_IGNORE_CASE operation, or null if fieldPath is not a StringExpression
     */
    private static Predicate buildEndsWithIgnoreCasePredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof StringExpression) {
            return ((StringExpression) fieldPath).endsWithIgnoreCase(value.toString());
        }
        return null;
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
        return null;
    }
    
    /**
     * Builds a NOT_BETWEEN predicate (exclusive range).
     * 
     * @param fieldPath The field path expression (must be ComparableExpression)
     * @param startValue The start value
     * @param endValue The end value
     * @return Predicate for NOT_BETWEEN operation, or null if fieldPath is not a ComparableExpression
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildNotBetweenPredicate(Object fieldPath, Object startValue, Object endValue) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.between((Comparable) startValue, (Comparable) endValue).not();
        }
        return null;
    }
    
    /**
     * Builds a GREATER_THAN predicate (>).
     * 
     * @param fieldPath The field path expression (must be ComparableExpression)
     * @param value The value to compare against
     * @return Predicate for GREATER_THAN operation, or null if fieldPath is not a ComparableExpression
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildGreaterThanPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.gt((Comparable) value);
        }
        return null;
    }
    
    /**
     * Builds a GREATER_THAN_OR_EQUAL predicate (>=).
     * 
     * @param fieldPath The field path expression (must be ComparableExpression)
     * @param value The value to compare against
     * @return Predicate for GREATER_THAN_OR_EQUAL operation, or null if fieldPath is not a ComparableExpression
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildGreaterThanOrEqualPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.goe((Comparable) value);
        }
        return null;
    }
    
    /**
     * Builds a LESS_THAN predicate (<).
     * 
     * @param fieldPath The field path expression (must be ComparableExpression)
     * @param value The value to compare against
     * @return Predicate for LESS_THAN operation, or null if fieldPath is not a ComparableExpression
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildLessThanPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.lt((Comparable) value);
        }
        return null;
    }
    
    /**
     * Builds a LESS_THAN_OR_EQUAL predicate (<=).
     * 
     * @param fieldPath The field path expression (must be ComparableExpression)
     * @param value The value to compare against
     * @return Predicate for LESS_THAN_OR_EQUAL operation, or null if fieldPath is not a ComparableExpression
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate buildLessThanOrEqualPredicate(Object fieldPath, Object value) {
        if (fieldPath instanceof ComparableExpression) {
            ComparableExpression<Comparable> expr = (ComparableExpression<Comparable>) fieldPath;
            return expr.loe((Comparable) value);
        }
        return null;
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
        if (fieldPath instanceof SimpleExpression && values != null && !values.isEmpty()) {
            // Validate size (should already be validated, but double-check for safety)
            if (values.size() > MAX_IN_VALUES) {
                throw new IllegalArgumentException(
                    String.format("IN operation: too many values. Maximum allowed: %d, provided: %d", 
                        MAX_IN_VALUES, values.size()));
            }
            return ((SimpleExpression<Object>) fieldPath).in(values);
        }
        if (fieldPath instanceof SimpleExpression && values != null && values.isEmpty()) {
            log.warn("IN operation with empty list - this will always return no results");
            // Return a predicate that's always false
            return ((SimpleExpression<Object>) fieldPath).in(List.of());
        }
        return null;
    }
    
    /**
     * Builds a NOT_IN predicate (value must not be in the provided list).
     * 
     * @param fieldPath The field path expression (must be SimpleExpression)
     * @param values The list of values to exclude
     * @return Predicate for NOT_IN operation, or null if fieldPath is not a SimpleExpression or values is null/empty
     */
    @SuppressWarnings("unchecked")
    private static Predicate buildNotInPredicate(Object fieldPath, List<Object> values) {
        if (fieldPath instanceof SimpleExpression && values != null && !values.isEmpty()) {
            return ((SimpleExpression<Object>) fieldPath).notIn(values);
        }
        return null;
    }
    
    /**
     * Builds an IS_NULL predicate (checks if field is null).
     * 
     * @param fieldPath The field path expression (must be SimpleExpression)
     * @return Predicate for IS_NULL operation, or null if fieldPath is not a SimpleExpression
     */
    @SuppressWarnings("unchecked")
    private static Predicate buildIsNullPredicate(Object fieldPath) {
        if (fieldPath instanceof SimpleExpression) {
            return ((SimpleExpression<Object>) fieldPath).isNull();
        }
        return null;
    }
    
    /**
     * Builds an IS_NOT_NULL predicate (checks if field is not null).
     * 
     * @param fieldPath The field path expression (must be SimpleExpression)
     * @return Predicate for IS_NOT_NULL operation, or null if fieldPath is not a SimpleExpression
     */
    @SuppressWarnings("unchecked")
    private static Predicate buildIsNotNullPredicate(Object fieldPath) {
        if (fieldPath instanceof SimpleExpression) {
            return ((SimpleExpression<Object>) fieldPath).isNotNull();
        }
        return null;
    }
    
    /**
     * Builds an IS_TRUE predicate (checks if boolean field is true).
     * 
     * @param fieldPath The field path expression (must be BooleanExpression)
     * @return Predicate for IS_TRUE operation, or null if fieldPath is not a BooleanExpression
     */
    private static Predicate buildIsTruePredicate(Object fieldPath) {
        if (fieldPath instanceof BooleanExpression) {
            return ((BooleanExpression) fieldPath).isTrue();
        }
        return null;
    }
    
    /**
     * Builds an IS_FALSE predicate (checks if boolean field is false).
     * 
     * @param fieldPath The field path expression (must be BooleanExpression)
     * @return Predicate for IS_FALSE operation, or null if fieldPath is not a BooleanExpression
     */
    private static Predicate buildIsFalsePredicate(Object fieldPath) {
        if (fieldPath instanceof BooleanExpression) {
            return ((BooleanExpression) fieldPath).isFalse();
        }
        return null;
    }
}
