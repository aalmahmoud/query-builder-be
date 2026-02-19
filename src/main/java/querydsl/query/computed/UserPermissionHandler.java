package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.StringExpression;
import querydsl.model.QUser;
import querydsl.model.User;
import querydsl.query.QueryCondition;
import querydsl.query.QueryOperation;
import org.springframework.stereotype.Component;

/**
 * Computed field handler for searching users by their role's permission names.
 *
 * <p>Allows querying with field name "permissionName" which traverses
 * user → role → permissions (ManyToMany) → name using QueryDSL's any().
 *
 * <p>Example query — find users that have the "user:create" permission:
 * <pre>
 * { "field": "permissionName", "operation": "EQUALS", "value": "user:create" }
 * </pre>
 *
 * <p>Note: You can also use the dot-notation "role.permissions.name" directly —
 * this handler is a convenience alias.
 */
@Component
public class UserPermissionHandler implements TypedComputedFieldHandler<User, QUser> {

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public String getFieldName() {
        return "permissionName";
    }

    @Override
    public Predicate buildPredicate(QUser qUser, QueryCondition condition) {
        StringExpression permName = qUser.role.permissions.any().name;
        String value = condition.getValue() != null ? condition.getValue().toString() : "";
        QueryOperation op = condition.getOperation() != null ? condition.getOperation() : QueryOperation.EQUALS;

        return switch (op) {
            case EQUALS -> permName.eq(value);
            case NOT_EQUALS -> permName.ne(value);
            case CONTAINS -> permName.contains(value);
            case NOT_CONTAINS -> permName.contains(value).not();
            case CONTAINS_IGNORE_CASE -> permName.containsIgnoreCase(value);
            case NOT_CONTAINS_IGNORE_CASE -> permName.containsIgnoreCase(value).not();
            case STARTS_WITH -> permName.startsWith(value);
            case STARTS_WITH_IGNORE_CASE -> permName.startsWithIgnoreCase(value);
            case ENDS_WITH -> permName.endsWith(value);
            case ENDS_WITH_IGNORE_CASE -> permName.endsWithIgnoreCase(value);
            case IN -> permName.in(condition.getValues().stream().map(Object::toString).toList());
            case NOT_IN -> permName.notIn(condition.getValues().stream().map(Object::toString).toList());
            case IS_NULL -> permName.isNull();
            case IS_NOT_NULL -> permName.isNotNull();
            default -> permName.eq(value);
        };
    }
}
