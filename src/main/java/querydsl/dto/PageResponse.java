package querydsl.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, explicit pagination envelope (review fix 5.9).
 *
 * <p>Spring has long warned against serializing {@code PageImpl} directly because its
 * JSON shape is an implementation detail that has changed across Spring Data versions.
 * Controllers return this record instead, so the wire contract is owned here and won't
 * drift on a framework bump.
 *
 * @param content       the page's items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total items across all pages
 * @param totalPages    total number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 * @param empty         whether this page has no content
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty());
    }
}
