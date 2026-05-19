package querydsl.query.computed;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import org.springframework.stereotype.Component;
import querydsl.model.QUser;
import querydsl.model.User;
import querydsl.query.QueryCondition;

/**
 * Computed field "fullName" → {@code concat(firstName, ' ', lastName)}.
 *
 * <p>Phase 5 fix 5.8: operation dispatch moved into
 * {@link StringFieldOperationDispatcher}.
 */
@Component
public class UserFullNameHandler implements TypedComputedFieldHandler<User, QUser> {

    private static final String FIELD = "fullName";

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
        StringExpression fullName = Expressions.stringTemplate(
                "concat({0}, ' ', {1})", qUser.firstName, qUser.lastName);
        return StringFieldOperationDispatcher.dispatch(fullName, condition, FIELD);
    }
}
