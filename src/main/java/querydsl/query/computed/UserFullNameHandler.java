package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import querydsl.model.QUser;
import querydsl.model.User;
import querydsl.query.QueryCondition;
import querydsl.query.QueryOperation;
import org.springframework.stereotype.Component;

/**
 * Computed field handler for searching users by full name (firstName + lastName).
 *
 * <p>Allows querying with field name "fullName" which concatenates
 * firstName and lastName with a space separator.
 *
 * <p>Example query:
 * <pre>
 * { "field": "fullName", "operation": "CONTAINS_IGNORE_CASE", "value": "john doe" }
 * </pre>
 */
@Component
public class UserFullNameHandler implements TypedComputedFieldHandler<User, QUser> {

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public String getFieldName() {
        return "fullName";
    }

    @Override
    public Predicate buildPredicate(QUser qUser, QueryCondition condition) {
        StringExpression fullName = Expressions.stringTemplate(
                "concat({0}, ' ', {1})", qUser.firstName, qUser.lastName);

        String value = condition.getValue() != null ? condition.getValue().toString() : "";
        QueryOperation op = condition.getOperation() != null ? condition.getOperation() : QueryOperation.CONTAINS_IGNORE_CASE;

        return switch (op) {
            case EQUALS -> fullName.eq(value);
            case NOT_EQUALS -> fullName.ne(value);
            case CONTAINS -> fullName.contains(value);
            case NOT_CONTAINS -> fullName.contains(value).not();
            case CONTAINS_IGNORE_CASE -> fullName.containsIgnoreCase(value);
            case NOT_CONTAINS_IGNORE_CASE -> fullName.containsIgnoreCase(value).not();
            case STARTS_WITH -> fullName.startsWith(value);
            case STARTS_WITH_IGNORE_CASE -> fullName.startsWithIgnoreCase(value);
            case ENDS_WITH -> fullName.endsWith(value);
            case ENDS_WITH_IGNORE_CASE -> fullName.endsWithIgnoreCase(value);
            default -> fullName.containsIgnoreCase(value);
        };
    }
}
