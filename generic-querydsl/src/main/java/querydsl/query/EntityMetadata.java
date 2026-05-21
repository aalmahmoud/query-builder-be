package querydsl.query;

import java.util.List;

/**
 * Self-describing metadata for an entity's query surface — the response of
 * {@code GET /{entity}/metadata}.
 *
 * @param entity the entity's simple name, lower-cased (e.g. {@code "user"})
 * @param fields the queryable fields, each with its type and valid operations
 */
public record EntityMetadata(String entity, List<FieldMeta> fields) {
}
