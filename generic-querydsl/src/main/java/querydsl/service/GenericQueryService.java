package querydsl.service;


import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import querydsl.exception.QueryException;
import querydsl.query.QueryRequest;
import querydsl.query.SortField;
import querydsl.query.SortableFields;
import querydsl.repository.GenericQueryRepository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic service for QueryDSL operations
 * Provides common query functionality that can be used by any entity service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenericQueryService {

    /**
     * Find all entities matching the given QueryRequest with pagination
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions and sorting
     * @param pageable Pagination information
     * @return Page of entities matching the query
     */
    public <T> Page<T> findAllByQueryRequest(GenericQueryRepository<T, ?> repository,
                                             QueryRequest queryRequest,
                                             Pageable pageable) {
        log.debug("Finding entities with query request: {} and pageable: {}", queryRequest, pageable);

        // Apply sorting from query request if provided
        Pageable finalPageable = applySortingFromQueryRequest(queryRequest, pageable, repository.getEntityClass());

        return repository.findAllByQueryRequest(queryRequest, finalPageable);
    }

    /**
     * Find all entities matching the given QueryRequest without pagination
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions and sorting
     * @return List of entities matching the query
     */
    public <T> List<T> findAllByQueryRequest(GenericQueryRepository<T, ?> repository,
                                             QueryRequest queryRequest) {
        log.debug("Finding all entities with query request: {}", queryRequest);
        return repository.findAllByQueryRequest(queryRequest);
    }

    /**
     * Count entities matching the given QueryRequest
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions
     * @return Number of entities matching the query
     */
    public <T> long countByQueryRequest(GenericQueryRepository<T, ?> repository,
                                        QueryRequest queryRequest) {
        log.debug("Counting entities with query request: {}", queryRequest);
        return repository.countByQueryRequest(queryRequest);
    }

    /**
     * Check if any entity exists matching the given QueryRequest
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions
     * @return true if any entity matches the query
     */
    public <T> boolean existsByQueryRequest(GenericQueryRepository<T, ?> repository,
                                            QueryRequest queryRequest) {
        log.debug("Checking existence with query request: {}", queryRequest);
        return repository.existsByQueryRequest(queryRequest);
    }

    /**
     * Find all entities matching the given QueryRequest with additional predicates using BooleanBuilder
     * This allows you to combine QueryRequest predicates with custom predicates based on business logic
     *
     * Example:
     *     BooleanBuilder additionalPredicates = new BooleanBuilder();
     *     if (roleName != null) {
     *         additionalPredicates.and(QUser.user.role.name.eq(roleName));
     *     }
     *     if (loggedInUser != null) {
     *         additionalPredicates.and(QUser.user.createdBy.eq(loggedInUser));
     *     }
     *     return genericQueryService.findAllByQueryRequestWithAdditionalPredicates(
     *         userRepository, queryRequest, additionalPredicates, pageable);
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions and sorting
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @param pageable Pagination information
     * @return Page of entities matching the query
     */
    public <T> Page<T> findAllByQueryRequestWithAdditionalPredicates(GenericQueryRepository<T, ?> repository,
                                                                     QueryRequest queryRequest,
                                                                     BooleanBuilder additionalPredicates,
                                                                     Pageable pageable) {
        log.debug("Finding entities with query request: {} and additional predicates with pageable: {}",
                queryRequest, pageable);

        // Apply sorting from query request if provided
        Pageable finalPageable = applySortingFromQueryRequest(queryRequest, pageable, repository.getEntityClass());

        return repository.findAllByQueryRequestWithAdditionalPredicates(queryRequest, additionalPredicates, finalPageable);
    }

    /**
     * Find all entities matching the given QueryRequest with additional predicates (without pagination)
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions and sorting
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @return List of entities matching the query
     */
    public <T> List<T> findAllByQueryRequestWithAdditionalPredicates(GenericQueryRepository<T, ?> repository,
                                                                     QueryRequest queryRequest,
                                                                     BooleanBuilder additionalPredicates) {
        log.debug("Finding all entities with query request: {} and additional predicates", queryRequest);
        return repository.findAllByQueryRequestWithAdditionalPredicates(queryRequest, additionalPredicates);
    }

    /**
     * Count entities matching the given QueryRequest with additional predicates
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @return Number of entities matching the query
     */
    public <T> long countByQueryRequestWithAdditionalPredicates(GenericQueryRepository<T, ?> repository,
                                                                QueryRequest queryRequest,
                                                                BooleanBuilder additionalPredicates) {
        log.debug("Counting entities with query request: {} and additional predicates", queryRequest);
        return repository.countByQueryRequestWithAdditionalPredicates(queryRequest, additionalPredicates);
    }

    /**
     * Check if any entity exists matching the given QueryRequest with additional predicates
     *
     * @param repository The repository to query
     * @param queryRequest The query request containing conditions
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @return true if any entity matches the query
     */
    public <T> boolean existsByQueryRequestWithAdditionalPredicates(GenericQueryRepository<T, ?> repository,
                                                                    QueryRequest queryRequest,
                                                                    BooleanBuilder additionalPredicates) {
        log.debug("Checking existence with query request: {} and additional predicates", queryRequest);
        return repository.existsByQueryRequestWithAdditionalPredicates(queryRequest, additionalPredicates);
    }

    /**
     * Apply sorting from QueryRequest to Pageable.
     *
     * <p>Phase 4 fix 4.2: every requested sort field is validated against the entity's
     * {@link SortableFields @SortableFields} allow-list. Entities without the annotation
     * fall back to a conservative default of {@code "id"} and {@code "createdDate"}.
     * Unknown fields produce a 400 with a parseable message rather than running a
     * pathological JOIN.
     */
    private Pageable applySortingFromQueryRequest(QueryRequest queryRequest, Pageable pageable, Class<?> entityClass) {
        if (queryRequest == null || queryRequest.getSortFields() == null || queryRequest.getSortFields().isEmpty()) {
            return pageable;
        }

        Set<String> allowed = sortableFieldsOf(entityClass);
        for (SortField sf : queryRequest.getSortFields()) {
            if (sf == null || sf.getField() == null) {
                continue;
            }
            if (!allowed.contains(sf.getField())) {
                throw new QueryException(
                        "Sort field '" + sf.getField() + "' is not allowed on "
                                + entityClass.getSimpleName() + ". Allowed: " + allowed);
            }
        }

        List<Sort.Order> orders = queryRequest.getSortFields().stream()
                .map(sortField -> new Sort.Order(
                        sortField.getDirection() == SortField.SortDirection.ASC
                                ? Sort.Direction.ASC
                                : Sort.Direction.DESC,
                        sortField.getField()
                ))
                .toList();

        Sort sort = Sort.by(orders);

        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );
    }

    /** Defensive fallback when an entity ships without an explicit allow-list. */
    private static final Set<String> DEFAULT_SORTABLE_FIELDS = Set.of("id", "createdDate");

    private static final java.util.Map<Class<?>, Set<String>> SORTABLE_CACHE = new ConcurrentHashMap<>();

    private static Set<String> sortableFieldsOf(Class<?> entityClass) {
        return SORTABLE_CACHE.computeIfAbsent(entityClass, c -> {
            for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                SortableFields ann = cur.getAnnotation(SortableFields.class);
                if (ann != null) {
                    return Set.of(ann.value());
                }
            }
            return DEFAULT_SORTABLE_FIELDS;
        });
    }
}
