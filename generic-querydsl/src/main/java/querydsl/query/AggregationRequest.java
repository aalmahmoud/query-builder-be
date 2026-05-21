package querydsl.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /{entity}/aggregate} — group-by + metric aggregation over a filtered set.
 *
 * @param filter   optional pre-aggregation filter (a QueryRequest group)
 * @param groupBy  0..n field paths to group by (0 = whole-set aggregate)
 * @param metrics  the aggregate metrics to compute (at least one)
 */
public record AggregationRequest(
        @Valid QueryRequest filter,
        @Size(max = 5, message = "Maximum 5 group-by fields") List<String> groupBy,
        @NotEmpty(message = "At least one metric is required")
        @Size(max = 10, message = "Maximum 10 metrics")
        @Valid List<Metric> metrics) {

    public enum Fn { COUNT, SUM, AVG, MIN, MAX }

    /**
     * @param fn    aggregate function
     * @param field field path to aggregate; required for all except COUNT (where null = count rows)
     * @param alias result key in the response (defaults to {@code fn_field} if null)
     */
    public record Metric(@NotNull Fn fn, String field, String alias) {
        public String resolvedAlias() {
            if (alias != null && !alias.isBlank()) {
                return alias;
            }
            return field == null ? fn.name().toLowerCase() : (fn.name().toLowerCase() + "_" + field.replace('.', '_'));
        }
    }
}
