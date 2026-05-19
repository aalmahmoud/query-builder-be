package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import org.springframework.stereotype.Component;
import querydsl.model.QUser;
import querydsl.model.User;
import querydsl.query.QueryCondition;

/**
 * Computed alias {@code "roleName"} → {@code user.role.name}.
 * Phase 5 fix 5.8: dispatch via {@link StringFieldOperationDispatcher}.
 */
@Component
public class UserRoleNameHandler implements TypedComputedFieldHandler<User, QUser> {

    private static final String FIELD = "roleName";

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public String getFieldName() {
        return FIELD;
    }

    @Override
    public Predicate buildPredicate(QUser qUser, QueryCondition condition) {
        return StringFieldOperationDispatcher.dispatch(qUser.role.name, condition, FIELD);
    }
}
