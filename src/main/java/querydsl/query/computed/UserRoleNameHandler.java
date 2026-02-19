package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.StringExpression;
import querydsl.model.QUser;
import querydsl.model.User;
import querydsl.query.QueryCondition;
import querydsl.query.QueryOperation;
import org.springframework.stereotype.Component;

/**
 * Computed field handler for searching users by their role name.
 *
 * <p>Provides a flat "roleName" alias for the nested path "role.name",
 * making queries more intuitive for API consumers.
 *
 * <p>Example query:
 * <pre>
 * { "field": "roleName", "operation": "EQUALS", "value": "ADMIN" }
 * </pre>
 *
 * <p>Note: You can also use the dot-notation "role.name" directly —
 * this handler is a convenience alias.
 */
@Component
public class UserRoleNameHandler implements TypedComputedFieldHandler<User, QUser> {

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public String getFieldName() {
        return "roleName";
    }

    @Override
    public Predicate buildPredicate(QUser qUser, QueryCondition condition) {
        StringExpression roleName = qUser.role.name;
        String value = condition.getValue() != null ? condition.getValue().toString() : "";
        QueryOperation op = condition.getOperation() != null ? condition.getOperation() : QueryOperation.EQUALS;

        return switch (op) {
            case EQUALS -> roleName.eq(value);
            case NOT_EQUALS -> roleName.ne(value);
            case CONTAINS -> roleName.contains(value);
            case NOT_CONTAINS -> roleName.contains(value).not();
            case CONTAINS_IGNORE_CASE -> roleName.containsIgnoreCase(value);
            case NOT_CONTAINS_IGNORE_CASE -> roleName.containsIgnoreCase(value).not();
            case STARTS_WITH -> roleName.startsWith(value);
            case STARTS_WITH_IGNORE_CASE -> roleName.startsWithIgnoreCase(value);
            case ENDS_WITH -> roleName.endsWith(value);
            case ENDS_WITH_IGNORE_CASE -> roleName.endsWithIgnoreCase(value);
            case IN -> roleName.in(condition.getValues().stream().map(Object::toString).toList());
            case NOT_IN -> roleName.notIn(condition.getValues().stream().map(Object::toString).toList());
            case IS_NULL -> roleName.isNull();
            case IS_NOT_NULL -> roleName.isNotNull();
            default -> roleName.eq(value);
        };
    }
}
