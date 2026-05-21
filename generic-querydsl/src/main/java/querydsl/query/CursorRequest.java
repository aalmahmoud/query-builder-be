package querydsl.query;

import jakarta.validation.Valid;

/**
 * Body of {@code POST /{entity}/query/cursor}. Sort is fixed to
 * {@code createdDate DESC, id DESC} for a stable keyset, so {@code query.sortFields}
 * is ignored on this endpoint.
 *
 * @param query  the filter (conditions/groups); may be null/empty for an unfiltered scan
 * @param cursor opaque token from the previous page's {@code nextCursor}; null for page 1
 * @param size   page size (defaults applied by the service)
 */
public record CursorRequest(@Valid QueryRequest query, String cursor, Integer size) {
}
