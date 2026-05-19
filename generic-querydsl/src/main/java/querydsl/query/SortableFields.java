package querydsl.query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the set of dotted-path fields a client is allowed to sort by on this entity.
 *
 * <p>Phase 4 fix 4.2: previously the engine accepted any well-formed path matching the
 * field-name regex, which let clients sort by {@code role.permissions.id} — forcing a
 * collection join and pathological plans. With this annotation, the engine rejects
 * unlisted sort fields with a {@link querydsl.exception.QueryException}.
 *
 * <p>If an entity has no {@code @SortableFields} annotation, the engine falls back to
 * an extremely conservative default whitelist of {@code "id"} and {@code "createdDate"}.
 *
 * @see querydsl.service.GenericQueryService
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SortableFields {
    String[] value();
}
