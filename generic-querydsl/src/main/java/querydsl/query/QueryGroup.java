package querydsl.query;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A recursive boolean group of query conditions and nested sub-groups.
 *
 * <p>The group's predicate is the combination, using {@link #logic}, of:
 * <ul>
 *   <li>each leaf {@link QueryCondition} in {@link #conditions}, and</li>
 *   <li>the predicate of each nested {@link QueryGroup} in {@link #groups}.</li>
 * </ul>
 *
 * <p>This is what lets clients express {@code (A AND (B OR C))} — the flat
 * {@code conditions[]} of v1 is just a top-level group with {@code logic = AND}.
 *
 * @see QueryRequest
 * @see LogicOperator
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryGroup {

    /** How this group's conditions and nested groups are combined. Defaults to AND. */
    private LogicOperator logic = LogicOperator.AND;

    /** Leaf conditions of this group. */
    @Valid
    private List<QueryCondition> conditions;

    /** Nested sub-groups (recursive). */
    @Valid
    private List<QueryGroup> groups;
}
