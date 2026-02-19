package querydsl.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a sort field with direction for query requests.
 * 
 * <p>Used in QueryRequest to specify how results should be sorted.
 * Multiple sort fields can be specified for multi-level sorting.
 * 
 * <p>Example usage:
 * <pre>
 * SortField sortByDate = new SortField("createdDate", SortField.SortDirection.DESC);
 * SortField sortById = new SortField("id"); // Defaults to ASC
 * </pre>
 * 
 * @see QueryRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortField {
    
    /**
     * The field name to sort by
     */
    private String field;
    
    /**
     * The sort direction (ASC or DESC)
     */
    private SortDirection direction = SortDirection.ASC;
    
    /**
     * Convenience constructor with default ASC direction
     */
    public SortField(String field) {
        this.field = field;
        this.direction = SortDirection.ASC;
    }
    
    /**
     * Enum for sort directions
     */
    public enum SortDirection {
        ASC, DESC
    }
}
