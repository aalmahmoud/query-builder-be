package querydsl.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic query request that contains multiple conditions and sorting options.
 * 
 * <p>This replaces query parameters with a structured request body, providing
 * a type-safe and flexible way to build complex queries.
 * 
 * <p>Example usage:
 * <pre>
 * QueryRequest request = new QueryRequest(
 *     Arrays.asList(
 *         new QueryCondition("firstName", QueryOperation.CONTAINS, "John"),
 *         new QueryCondition("createdDate", QueryOperation.BETWEEN, startDate, endDate)
 *     ),
 *     Arrays.asList(
 *         new SortField("createdDate", SortField.SortDirection.DESC),
 *         new SortField("id", SortField.SortDirection.ASC)
 *     )
 * );
 * </pre>
 * 
 * <p>For type-safe query building in services, use {@link com.querydsl.core.BooleanBuilder}
 * with Q entities via {@link querydsl.service.GenericQueryService#findAllByQueryRequestWithAdditionalPredicates}
 * instead of modifying QueryRequest directly.
 * 
 * @see QueryCondition
 * @see SortField
 * @see querydsl.service.GenericQueryService
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {
    
    /**
     * List of query conditions to apply
     */
    @Valid
    @Size(max = 50, message = "Maximum 50 conditions allowed")
    private List<QueryCondition> conditions;
    
    /**
     * List of fields to sort by
     */
    @Valid
    @Size(max = 10, message = "Maximum 10 sort fields allowed")
    private List<SortField> sortFields;
    
    /**
     * Convenience constructor for simple queries with conditions only
     */
    public QueryRequest(List<QueryCondition> conditions) {
        this.conditions = conditions;
    }
    
    /**
     * Convenience constructor for single condition queries
     */
    public QueryRequest(QueryCondition condition) {
        this.conditions = List.of(condition);
    }
}
