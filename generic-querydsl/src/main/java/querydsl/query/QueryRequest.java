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
     * How this request's top-level {@link #conditions} and {@link #groups} are combined.
     * Defaults to {@link LogicOperator#AND} when {@code null} (v1 compatibility).
     */
    private LogicOperator logic;

    /**
     * Top-level query conditions. With {@link #logic} (default AND) these form the
     * outermost boolean group. A v1 request that only sets {@code conditions} behaves
     * exactly as before.
     */
    @Valid
    @Size(max = QueryPredicateBuilder.MAX_CONDITIONS,
            message = "Maximum 50 conditions allowed")
    private List<QueryCondition> conditions;

    /**
     * Nested boolean groups (recursive), enabling {@code (A AND (B OR C))}. Combined with
     * {@link #conditions} using {@link #logic}.
     */
    @Valid
    private List<QueryGroup> groups;

    /**
     * List of fields to sort by
     */
    @Valid
    @Size(max = 10, message = "Maximum 10 sort fields allowed")
    private List<SortField> sortFields;

    /**
     * Optional projection (sparse fieldset). When non-empty, {@code /query} returns flat
     * maps keyed by these dot-path field names instead of full DTOs. {@code null}/empty =
     * full DTO. Selected fields are subject to the same allow-list as filtering.
     */
    @Valid
    @Size(max = 50, message = "Maximum 50 selected fields allowed")
    private List<String> select;

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
