package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import org.springframework.stereotype.Component;
import querydsl.model.QRole;
import querydsl.model.Role;
import querydsl.query.QueryCondition;

/**
 * Computed alias {@code "permissionName"} on {@link Role} →
 * {@code role.permissions.any().name}.
 * Phase 5 fix 5.8: dispatch via {@link StringFieldOperationDispatcher}.
 */
@Component
public class RolePermissionNameHandler implements TypedComputedFieldHandler<Role, QRole> {

    private static final String FIELD = "permissionName";

    @Override
    public Class<Role> getEntityClass() {
        return Role.class;
    }

    @Override
    public String getFieldName() {
        return FIELD;
    }

    @Override
    public Predicate buildPredicate(QRole qRole, QueryCondition condition) {
        return StringFieldOperationDispatcher.dispatch(
                qRole.permissions.any().name, condition, FIELD);
    }
}
