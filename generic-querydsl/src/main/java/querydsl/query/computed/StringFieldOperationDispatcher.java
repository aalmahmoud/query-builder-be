package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.StringExpression;
import querydsl.exception.QueryException;
import querydsl.query.QueryCondition;
import querydsl.query.QueryOperation;

import java.util.List;

/**
 * Shared dispatch helper for {@link TypedComputedFieldHandler} implementations that
 * resolve to a {@link StringExpression}. Avoids the 50-line operation-switch being
 * duplicated four times across the demo handlers.
 *
 * <p>Phase 5 fix 5.8.
 */
public final class StringFieldOperationDispatcher {

    private StringFieldOperationDispatcher() {
        // utility
    }

    /**
     * @param expression the QueryDSL string-typed expression to compare against
     * @param condition  the incoming condition (operation + value(s))
     * @param fieldLabel a human label used in error messages — e.g. {@code "fullName"}
     */
    public static Predicate dispatch(StringExpression expression, QueryCondition condition, String fieldLabel) {
        QueryOperation op = condition.getOperation() != null ? condition.getOperation() : QueryOperation.EQUALS;
        String value = condition.getValue() != null ? condition.getValue().toString() : "";

        return switch (op) {
            case EQUALS     -> expression.eq(value);
            case NOT_EQUALS -> expression.ne(value);

            case CONTAINS                 -> expression.contains(value);
            case NOT_CONTAINS             -> expression.contains(value).not();
            case CONTAINS_IGNORE_CASE     -> expression.containsIgnoreCase(value);
            case NOT_CONTAINS_IGNORE_CASE -> expression.containsIgnoreCase(value).not();

            case STARTS_WITH                  -> expression.startsWith(value);
            case NOT_STARTS_WITH              -> expression.startsWith(value).not();
            case STARTS_WITH_IGNORE_CASE      -> expression.startsWithIgnoreCase(value);
            case NOT_STARTS_WITH_IGNORE_CASE  -> expression.startsWithIgnoreCase(value).not();

            case ENDS_WITH                    -> expression.endsWith(value);
            case NOT_ENDS_WITH                -> expression.endsWith(value).not();
            case ENDS_WITH_IGNORE_CASE        -> expression.endsWithIgnoreCase(value);
            case NOT_ENDS_WITH_IGNORE_CASE    -> expression.endsWithIgnoreCase(value).not();

            case IN     -> expression.in(stringValues(condition));
            case NOT_IN -> expression.notIn(stringValues(condition));

            case IS_NULL     -> expression.isNull();
            case IS_NOT_NULL -> expression.isNotNull();

            default -> throw new QueryException(
                    "Operation " + op + " is not supported on the computed field '" + fieldLabel + "'");
        };
    }

    private static List<String> stringValues(QueryCondition condition) {
        if (condition.getValues() == null) {
            throw new QueryException("Operation " + condition.getOperation() + " requires a 'values' list");
        }
        return condition.getValues().stream().map(Object::toString).toList();
    }
}
