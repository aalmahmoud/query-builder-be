package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.StringExpression;
import querydsl.model.QRole;
import querydsl.model.Role;
import querydsl.query.QueryCondition;
import querydsl.query.QueryOperation;
import org.springframework.stereotype.Component;

/**
 * Computed field handler for searching roles by their permission names.
 *
 * <p>Allows querying with field name "permissionName" which traverses
 * role → permissions (ManyToMany) → name using QueryDSL's any().
 *
 * <p>Example query — find roles that include the "user:delete" permission:
 * <pre>
 * { "field": "permissionName", "operation": "EQUALS", "value": "user:delete" }
 * </pre>
 */
@Component
public class RolePermissionNameHandler implements TypedComputedFieldHandler<Role, QRole> {

    @Override
    public Class<Role> getEntityClass() {
        return Role.class;
    }

    @Override
    public String getFieldName() {
        return "permissionName";
    }

    @Override
    public Predicate buildPredicate(QRole qRole, QueryCondition condition) {
        StringExpression permName = qRole.permissions.any().name;
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
            case IN -> permName.in(condition.getValues().stream().map(Object::toString).toList());
            case NOT_IN -> permName.notIn(condition.getValues().stream().map(Object::toString).toList());
            case IS_NULL -> permName.isNull();
            case IS_NOT_NULL -> permName.isNotNull();
            default -> permName.eq(value);
        };
    }
}
