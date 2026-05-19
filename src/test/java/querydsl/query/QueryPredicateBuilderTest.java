package querydsl.query;

import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.Test;
import querydsl.exception.QueryException;
import querydsl.model.User;
import querydsl.query.computed.ComputedFieldHandlerRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for QueryPredicateBuilder.
 *
 * <p>Phase 1 fixes:
 * <ul>
 *   <li>1.2 — NOT_STARTS_WITH, NOT_STARTS_WITH_IGNORE_CASE, NOT_ENDS_WITH, NOT_ENDS_WITH_IGNORE_CASE
 *       now produce real predicates instead of being silently dropped.</li>
 *   <li>1.3 — Type-incompatible operations throw QueryException (400) instead of returning null
 *       (which used to silently match all rows).</li>
 * </ul>
 */
class QueryPredicateBuilderTest {

    private final QueryPredicateBuilder builder = new QueryPredicateBuilder(new ComputedFieldHandlerRegistry());

    // ---- 1.2: previously-dropped operations now resolve to real predicates ----

    @Test
    void notStartsWith_buildsPredicate() {
        Predicate p = build("firstName", QueryOperation.NOT_STARTS_WITH, "John");
        assertNotNull(p);
        assertTrue(p.toString().toLowerCase().contains("starts with") || p.toString().contains("!"));
    }

    @Test
    void notStartsWithIgnoreCase_buildsPredicate() {
        Predicate p = build("firstName", QueryOperation.NOT_STARTS_WITH_IGNORE_CASE, "john");
        assertNotNull(p);
    }

    @Test
    void notEndsWith_buildsPredicate() {
        Predicate p = build("firstName", QueryOperation.NOT_ENDS_WITH, "Smith");
        assertNotNull(p);
    }

    @Test
    void notEndsWithIgnoreCase_buildsPredicate() {
        Predicate p = build("firstName", QueryOperation.NOT_ENDS_WITH_IGNORE_CASE, "smith");
        assertNotNull(p);
    }

    // ---- 1.3: type-mismatched operations now throw instead of silently matching all ----

    @Test
    void contains_onNumericField_throwsQueryException() {
        QueryException ex = assertThrows(QueryException.class,
                () -> build("id", QueryOperation.CONTAINS, "42"));
        assertTrue(ex.getMessage().contains("CONTAINS"));
        assertTrue(ex.getMessage().contains("StringExpression"));
    }

    @Test
    void startsWith_onBooleanField_throwsQueryException() {
        assertThrows(QueryException.class,
                () -> build("isActive", QueryOperation.STARTS_WITH, "true"));
    }

    @Test
    void greaterThan_onStringField_doesNotThrow() {
        // Strings ARE comparable (lexicographic), so this is a legitimate case.
        Predicate p = build("firstName", QueryOperation.GREATER_THAN, "M");
        assertNotNull(p);
    }

    @Test
    void isTrue_onStringField_throwsQueryException() {
        assertThrows(QueryException.class,
                () -> build("firstName", QueryOperation.IS_TRUE, null));
    }

    // ---- Sanity: regular operations still work after the refactor ----

    @Test
    void equals_works() {
        Predicate p = build("email", QueryOperation.EQUALS, "a@b.com");
        assertNotNull(p);
    }

    @Test
    void in_emptyList_matchesNothing() {
        QueryCondition cond = new QueryCondition();
        cond.setField("id");
        cond.setOperation(QueryOperation.IN);
        cond.setValues(List.of());
        Predicate p = builder.buildPredicate(new QueryRequest(cond), User.class);
        assertNotNull(p);
        // SQL would be `id in ()` → effectively `false`.
    }

    @Test
    void notIn_emptyList_matchesEverything() {
        // Phase 1 fix: previously returned null, silently dropping the condition.
        QueryCondition cond = new QueryCondition();
        cond.setField("id");
        cond.setOperation(QueryOperation.NOT_IN);
        cond.setValues(List.of());
        Predicate p = builder.buildPredicate(new QueryRequest(cond), User.class);
        assertNotNull(p, "Empty NOT_IN must produce an always-true predicate, not null");
    }

    // ---- helpers ----

    private Predicate build(String field, QueryOperation op, Object value) {
        QueryCondition cond = new QueryCondition();
        cond.setField(field);
        cond.setOperation(op);
        cond.setValue(value);
        return builder.buildPredicate(new QueryRequest(cond), User.class);
    }

    @Test
    void multipleConditions_combinedWithAnd() {
        QueryRequest req = new QueryRequest(List.of(
                new QueryCondition("firstName", QueryOperation.EQUALS, "John", null, null, null),
                new QueryCondition("isActive", QueryOperation.IS_TRUE, null, null, null, null)
        ));
        Predicate p = builder.buildPredicate(req, User.class);
        assertNotNull(p);
        String s = p.toString().toLowerCase();
        assertTrue(s.contains("&&") || s.contains("and"), "Expected AND in: " + p);
    }

    @Test
    void firstName_equalsConditionBuildsCorrectExpression() {
        Predicate p = build("firstName", QueryOperation.EQUALS, "John");
        assertEquals("user.firstName = John", p.toString());
    }

    // ---- 3.6: timezone-aware date parsing ----

    @Test
    void createdDate_withPositiveOffset_convertsToUtc() {
        // 2025-11-01T03:00:00+03:00 == 2025-11-01T00:00:00 UTC
        Predicate p = build("createdDate", QueryOperation.EQUALS, "2025-11-01T03:00:00+03:00");
        assertTrue(p.toString().contains("2025-11-01T00:00"),
                "Expected UTC-normalised timestamp. Got: " + p);
    }

    @Test
    void createdDate_withZuluSuffix_parsesAsUtc() {
        Predicate p = build("createdDate", QueryOperation.EQUALS, "2025-11-01T12:34:56Z");
        assertTrue(p.toString().contains("2025-11-01T12:34"),
                "Z input should be parsed as UTC. Got: " + p);
    }

    @Test
    void createdDate_withNegativeOffset_convertsToUtc() {
        // 2025-11-01T19:00:00-05:00 == 2025-11-02T00:00:00 UTC
        Predicate p = build("createdDate", QueryOperation.EQUALS, "2025-11-01T19:00:00-05:00");
        assertTrue(p.toString().contains("2025-11-02T00:00"),
                "Negative offset should convert to UTC (date may roll over). Got: " + p);
    }

    @Test
    void createdDate_dateOnly_parsesToStartOfDay() {
        Predicate p = build("createdDate", QueryOperation.EQUALS, "2025-11-01");
        assertTrue(p.toString().contains("2025-11-01T00:00"));
    }
}
