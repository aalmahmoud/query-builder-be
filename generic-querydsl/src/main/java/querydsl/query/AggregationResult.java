package querydsl.query;

import java.util.List;
import java.util.Map;

/**
 * Result of {@code POST /{entity}/aggregate}.
 *
 * @param rows one row per group; each carries the group key values and the computed metrics
 */
public record AggregationResult(List<Row> rows) {

    /**
     * @param group   group-by field path → value (empty when there is no group-by)
     * @param metrics metric alias → numeric value
     */
    public record Row(Map<String, Object> group, Map<String, Object> metrics) {
    }
}
