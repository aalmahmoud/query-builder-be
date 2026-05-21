package querydsl.query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the set of dotted-path fields a client is allowed to <em>filter</em> (and
 * project / group-by) on this entity.
 *
 * <p>Without this annotation the engine accepts any well-formed field path (subject only
 * to the field-name regex and depth cap) — which lets a client filter by sensitive columns
 * such as {@code password} and use the boolean response of {@code /exists} or {@code /query}
 * as an oracle to extract their value. With this annotation the engine rejects any condition,
 * projected field, or group-by field that is neither listed here nor a registered computed
 * field, returning a {@link querydsl.exception.QueryException} (HTTP 400).
 *
 * <p>Mirrors {@link SortableFields} (which governs sorting). Computed fields are always
 * permitted regardless of this list.
 *
 * @see querydsl.query.QueryPredicateBuilder
 * @see SortableFields
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FilterableFields {
    String[] value();
}
