package querydsl.query;

/**
 * Enum representing different query operations supported by the generic QueryDSL system.
 * 
 * <p>Supported operations:
 * <ul>
 *   <li><b>EQUALS / NOT_EQUALS</b> - Exact match / negation</li>
 *   <li><b>CONTAINS / NOT_CONTAINS</b> - Substring match (case-sensitive) / negation</li>
 *   <li><b>CONTAINS_IGNORE_CASE / NOT_CONTAINS_IGNORE_CASE</b> - Substring match (case-insensitive) / negation</li>
 *   <li><b>STARTS_WITH / NOT_STARTS_WITH</b> - Prefix match (case-sensitive) / negation</li>
 *   <li><b>STARTS_WITH_IGNORE_CASE / NOT_STARTS_WITH_IGNORE_CASE</b> - Prefix match (case-insensitive) / negation</li>
 *   <li><b>ENDS_WITH / NOT_ENDS_WITH</b> - Suffix match (case-sensitive) / negation</li>
 *   <li><b>ENDS_WITH_IGNORE_CASE / NOT_ENDS_WITH_IGNORE_CASE</b> - Suffix match (case-insensitive) / negation</li>
 *   <li><b>BETWEEN / NOT_BETWEEN</b> - Range check (inclusive) / negation</li>
 *   <li><b>GREATER_THAN / GREATER_THAN_OR_EQUAL</b> - Comparison operations</li>
 *   <li><b>LESS_THAN / LESS_THAN_OR_EQUAL</b> - Comparison operations</li>
 *   <li><b>IN / NOT_IN</b> - List membership check / negation</li>
 *   <li><b>IS_NULL / IS_NOT_NULL</b> - Null check / negation</li>
 *   <li><b>IS_TRUE / IS_FALSE</b> - Boolean check</li>
 * </ul>
 */
public enum QueryOperation {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    CONTAINS_IGNORE_CASE,
    NOT_CONTAINS_IGNORE_CASE,
    STARTS_WITH,
    NOT_STARTS_WITH,
    STARTS_WITH_IGNORE_CASE,
    NOT_STARTS_WITH_IGNORE_CASE,
    ENDS_WITH,
    NOT_ENDS_WITH,
    ENDS_WITH_IGNORE_CASE,
    NOT_ENDS_WITH_IGNORE_CASE,
    BETWEEN,
    NOT_BETWEEN,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    IS_NULL,
    IS_NOT_NULL,
    IS_TRUE,
    IS_FALSE
}
