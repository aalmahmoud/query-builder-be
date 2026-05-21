package querydsl.service;


import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import querydsl.exception.QueryException;
import querydsl.query.AggregationRequest;
import querydsl.query.AggregationResult;
import querydsl.query.CursorPage;
import querydsl.query.LogicOperator;
import querydsl.query.QueryCondition;
import querydsl.query.QueryGroup;
import querydsl.query.QueryOperation;
import querydsl.query.QueryPredicateBuilder;
import querydsl.query.QueryRequest;
import querydsl.query.SortField;
import querydsl.query.SortableFields;
import querydsl.repository.GenericQueryRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @PersistenceContext
    private EntityManager entityManager;

    private static final int DEFAULT_CURSOR_SIZE = 20;
    private static final int MAX_CURSOR_SIZE = 200;

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

    // ================= projections (sparse fieldsets) =================

    /**
     * Runs a projected query: when {@code queryRequest.select} is non-empty, returns flat
     * maps (field path → value) instead of full entities. The select fields are validated
     * against the entity's filterable allow-list by {@code buildPredicate}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Page<Map<String, Object>> queryProjection(GenericQueryRepository<?, ?> repository,
                                                      QueryRequest queryRequest,
                                                      Pageable pageable) {
        Class<?> entityClass = repository.getEntityClass();
        QueryPredicateBuilder builder = QueryPredicateBuilder.getInstance();
        Predicate predicate = builder.buildPredicate(queryRequest, entityClass);

        List<String> select = queryRequest.getSelect();
        EntityPath<?> root = builder.getEntityPath(entityClass);
        Expression<?>[] exprs = new Expression<?>[select.size()];
        for (int i = 0; i < select.size(); i++) {
            exprs[i] = builder.resolveFieldExpression(entityClass, select.get(i));
        }

        Pageable fp = applySortingFromQueryRequest(queryRequest, pageable, entityClass);

        JPAQuery<Tuple> q = new JPAQuery<>(entityManager).select(exprs).from(root);
        if (predicate != null) {
            q.where(predicate);
        }
        for (OrderSpecifier<?> os : toOrderSpecifiers(queryRequest, entityClass)) {
            q.orderBy(os);
        }
        List<Tuple> tuples = q.offset(fp.getOffset()).limit(fp.getPageSize()).fetch();

        JPAQuery<?> countQuery = new JPAQuery<>(entityManager).from(root);
        if (predicate != null) {
            countQuery.where(predicate);
        }
        long total = countQuery.fetchCount();

        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < select.size(); i++) {
                row.put(select.get(i), t.get(exprs[i]));
            }
            rows.add(row);
        }
        return new PageImpl<>(rows, fp, total);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<OrderSpecifier<?>> toOrderSpecifiers(QueryRequest qr, Class<?> entityClass) {
        QueryPredicateBuilder builder = QueryPredicateBuilder.getInstance();
        List<OrderSpecifier<?>> out = new ArrayList<>();
        if (qr.getSortFields() == null) {
            return out;
        }
        for (SortField sf : qr.getSortFields()) {
            if (sf == null || sf.getField() == null) {
                continue;
            }
            Expression<?> e = builder.resolveFieldExpression(entityClass, sf.getField());
            if (e instanceof ComparableExpressionBase<?> cmp) {
                Order order = sf.getDirection() == SortField.SortDirection.ASC ? Order.ASC : Order.DESC;
                out.add(new OrderSpecifier(order, cmp));
            }
        }
        return out;
    }

    // ================= keyset (cursor) pagination =================

    /**
     * Keyset pagination with a fixed, stable sort of {@code createdDate DESC, id DESC}.
     * The cursor encodes the last row's {@code (createdDate, id)}; the keyset predicate is
     * expressed as an OR-group and combined with the caller's filter — reusing the engine.
     */
    public <T> CursorPage<T> queryByCursor(GenericQueryRepository<T, ?> repository,
                                           QueryRequest query, String cursor, Integer size) {
        int limit = size == null ? DEFAULT_CURSOR_SIZE : Math.max(1, Math.min(size, MAX_CURSOR_SIZE));
        Class<?> entityClass = repository.getEntityClass();

        // Keyset on the unique, monotonic primary key (id DESC = newest first). Using id
        // alone avoids timestamp-precision pitfalls and needs no tie-breaker.
        QueryRequest eff = new QueryRequest();
        eff.setLogic(LogicOperator.AND);
        List<QueryCondition> conditions = new ArrayList<>();
        if (cursor != null && !cursor.isBlank()) {
            conditions.add(cond("id", QueryOperation.LESS_THAN, decodeCursorId(cursor)));
        }
        eff.setConditions(conditions);
        if (query != null && ((query.getConditions() != null && !query.getConditions().isEmpty())
                || (query.getGroups() != null && !query.getGroups().isEmpty()))) {
            QueryGroup userGroup = new QueryGroup();
            userGroup.setLogic(query.getLogic() == null ? LogicOperator.AND : query.getLogic());
            userGroup.setConditions(query.getConditions());
            userGroup.setGroups(query.getGroups());
            eff.setGroups(List.of(userGroup));
        }
        eff.setSortFields(List.of(new SortField("id", SortField.SortDirection.DESC)));

        Pageable fp = applySortingFromQueryRequest(eff, PageRequest.of(0, limit + 1), entityClass);
        List<T> fetched = repository.findAllByQueryRequest(eff, fp).getContent();

        boolean hasNext = fetched.size() > limit;
        List<T> content = new ArrayList<>(hasNext ? fetched.subList(0, limit) : fetched);
        String nextCursor = (hasNext && !content.isEmpty()) ? encodeCursor(content.get(content.size() - 1)) : null;
        return new CursorPage<>(content, nextCursor, hasNext);
    }

    private static Long decodeCursorId(String cursor) {
        try {
            return Long.valueOf(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new QueryException("Malformed cursor");
        }
    }

    private static QueryCondition cond(String field, QueryOperation op, Object value) {
        QueryCondition c = new QueryCondition();
        c.setField(field);
        c.setOperation(op);
        c.setValue(value);
        return c;
    }

    private String encodeCursor(Object entity) {
        Object id = invokeGetter(entity, "getId");
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
    }

    private static Object invokeGetter(Object o, String method) {
        try {
            return o.getClass().getMethod(method).invoke(o);
        } catch (ReflectiveOperationException e) {
            throw new QueryException("Cursor pagination requires id and createdDate on "
                    + o.getClass().getSimpleName());
        }
    }

    // ================= aggregations =================

    /**
     * Group-by + metric aggregation. group-by and metric fields must be filterable; SUM/AVG/
     * MIN/MAX require numeric fields; COUNT with a null field counts rows.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public AggregationResult aggregate(GenericQueryRepository<?, ?> repository, AggregationRequest req) {
        Class<?> entityClass = repository.getEntityClass();
        QueryPredicateBuilder builder = QueryPredicateBuilder.getInstance();
        Predicate predicate = req.filter() == null ? null : builder.buildPredicate(req.filter(), entityClass);
        EntityPath<?> root = builder.getEntityPath(entityClass);

        List<String> groupBy = req.groupBy() == null ? List.of() : req.groupBy();
        List<Expression<?>> groupExprs = new ArrayList<>();
        for (String g : groupBy) {
            builder.assertFieldFilterable(entityClass, g);
            groupExprs.add(builder.resolveFieldExpression(entityClass, g));
        }

        List<Expression<?>> metricExprs = new ArrayList<>();
        for (AggregationRequest.Metric m : req.metrics()) {
            metricExprs.add(metricExpression(builder, entityClass, m));
        }

        List<Expression<?>> selectList = new ArrayList<>(groupExprs);
        selectList.addAll(metricExprs);

        JPAQuery<Tuple> q = new JPAQuery<>(entityManager)
                .select(selectList.toArray(new Expression[0])).from(root);
        if (predicate != null) {
            q.where(predicate);
        }
        if (!groupExprs.isEmpty()) {
            q.groupBy(groupExprs.toArray(new Expression[0]));
        }
        List<Tuple> tuples = q.fetch();

        List<AggregationResult.Row> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            Map<String, Object> group = new LinkedHashMap<>();
            for (int i = 0; i < groupBy.size(); i++) {
                group.put(groupBy.get(i), t.get(groupExprs.get(i)));
            }
            Map<String, Object> metrics = new LinkedHashMap<>();
            for (int j = 0; j < req.metrics().size(); j++) {
                metrics.put(req.metrics().get(j).resolvedAlias(), t.get(metricExprs.get(j)));
            }
            rows.add(new AggregationResult.Row(group, metrics));
        }
        return new AggregationResult(rows);
    }

    private Expression<?> metricExpression(QueryPredicateBuilder builder, Class<?> entityClass,
                                           AggregationRequest.Metric m) {
        if (m.fn() == AggregationRequest.Fn.COUNT) {
            if (m.field() == null) {
                return Wildcard.count; // count(*)
            }
            builder.assertFieldFilterable(entityClass, m.field());
            Expression<?> e = builder.resolveFieldExpression(entityClass, m.field());
            if (e instanceof SimpleExpression<?> se) {
                return se.count();
            }
            return Wildcard.count;
        }
        if (m.field() == null) {
            throw new QueryException(m.fn() + " requires a field");
        }
        builder.assertFieldFilterable(entityClass, m.field());
        Expression<?> e = builder.resolveFieldExpression(entityClass, m.field());
        if (!(e instanceof NumberPath<?> np)) {
            throw new QueryException(m.fn() + " requires a numeric field, got '" + m.field() + "'");
        }
        return switch (m.fn()) {
            case SUM -> np.sum();
            case AVG -> np.avg();
            case MIN -> np.min();
            case MAX -> np.max();
            default -> throw new IllegalStateException("unreachable");
        };
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
