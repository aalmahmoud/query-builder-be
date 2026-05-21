package querydsl.query;

import java.util.List;

/**
 * Keyset (cursor) pagination result. Unlike offset pagination, this is stable and
 * cheap on large tables.
 *
 * @param content    the page's items
 * @param nextCursor opaque token to fetch the next page (null when there is no next page)
 * @param hasNext    whether another page exists
 */
public record CursorPage<T>(List<T> content, String nextCursor, boolean hasNext) {
}
