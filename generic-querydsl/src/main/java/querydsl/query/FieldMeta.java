package querydsl.query;

import java.util.List;

/**
 * Describes one queryable field of an entity for the self-describing
 * {@code GET /{entity}/metadata} endpoint, so a client can render a query builder
 * without hard-coding field lists or operation sets.
 *
 * @param name       dotted path, e.g. {@code "email"} or {@code "role.name"}
 * @param label      human-friendly label, e.g. {@code "Role Name"}
 * @param type       logical type: string|number|boolean|date|datetime|enum|uuid
 * @param operations the {@link QueryOperation} names valid for this field's type
 * @param sortable   whether the field may be sorted on (per {@code @SortableFields})
 * @param filterable whether the field may be filtered on (per {@code @FilterableFields})
 * @param computed   whether the field is backed by a computed-field handler
 * @param enumValues for {@code enum} types, the allowed constant names; otherwise null
 */
public record FieldMeta(
        String name,
        String label,
        String type,
        List<String> operations,
        boolean sortable,
        boolean filterable,
        boolean computed,
        List<String> enumValues) {
}
