package querydsl.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic query condition that can be used to build QueryDSL predicates.
 * 
 * <p>Supports various operations like equals, contains, between, etc.
 * If no operation is specified, defaults to EQUALS for convenience.
 * 
 * <p>Example usage:
 * <pre>
 * // Simple equals condition
 * QueryCondition condition = new QueryCondition("firstName", QueryOperation.EQUALS, "John");
 * 
 * // IN condition with multiple values
 * QueryCondition inCondition = new QueryCondition("role.name", QueryOperation.IN, 
 *     Arrays.asList("ADMIN", "USER"));
 * 
 * // BETWEEN condition for date range
 * QueryCondition betweenCondition = new QueryCondition("createdDate", 
 *     LocalDateTime.of(2025, 1, 1, 0, 0),
 *     LocalDateTime.of(2025, 12, 31, 23, 59));
 * </pre>
 * 
 * @see QueryOperation
 * @see QueryRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryCondition {
    
    /**
     * The field name to query on (e.g., "firstName", "email", "role.id")
     */
    @NotBlank(message = "Field name is required")
    @Size(max = 200, message = "Field name must not exceed 200 characters")
    private String field;
    
    /**
     * The operation to perform (e.g., EQUALS, CONTAINS, BETWEEN)
     * If null, defaults to EQUALS for convenience
     */
    private QueryOperation operation = QueryOperation.EQUALS;
    
    /**
     * Single value for operations like EQUALS, CONTAINS, etc.
     */
    private Object value;
    
    /**
     * List of values for operations like IN, NOT_IN
     */
    @Size(max = QueryPredicateBuilder.MAX_IN_VALUES,
            message = "Maximum 200 values allowed in IN operation")
    private List<Object> values;
    
    /**
     * Start value for BETWEEN operations
     */
    private Object startValue;
    
    /**
     * End value for BETWEEN operations
     */
    private Object endValue;
}
