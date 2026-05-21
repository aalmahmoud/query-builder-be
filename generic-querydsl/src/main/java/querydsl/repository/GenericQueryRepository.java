package querydsl.repository;


import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import querydsl.query.QueryPredicateBuilder;
import querydsl.query.QueryRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Generic repository interface that extends JpaRepository and QuerydslPredicateExecutor
 * Provides QueryDSL functionality for any entity
 */
@NoRepositoryBean
public interface GenericQueryRepository<T, ID> extends JpaRepository<T, ID>, QuerydslPredicateExecutor<T> {

    /**
     * Find all entities matching the given QueryRequest with pagination
     *
     * @param queryRequest The query request containing conditions and sorting
     * @param pageable Pagination information
     * @return Page of entities matching the query
     */
    default Page<T> findAllByQueryRequest(QueryRequest queryRequest, Pageable pageable) {
        Predicate predicate = QueryPredicateBuilder.getInstance()
                .buildPredicate(queryRequest, getEntityClass());
        // A null predicate means "no filter" (empty/whole-set query) — fetch all, sorted.
        return predicate == null ? findAll(pageable) : findAll(predicate, pageable);
    }

    /**
     * Find all entities matching the given QueryRequest without pagination
     *
     * @param queryRequest The query request containing conditions and sorting
     * @return List of entities matching the query
     */
    default List<T> findAllByQueryRequest(QueryRequest queryRequest) {
        Predicate predicate = QueryPredicateBuilder.getInstance()
                .buildPredicate(queryRequest, getEntityClass());
        if (predicate == null) {
            return findAll();
        }
        return StreamSupport.stream(findAll(predicate).spliterator(), false).toList();
    }

    /**
     * Count entities matching the given QueryRequest
     *
     * @param queryRequest The query request containing conditions
     * @return Number of entities matching the query
     */
    default long countByQueryRequest(QueryRequest queryRequest) {
        Predicate predicate = QueryPredicateBuilder.getInstance()
                .buildPredicate(queryRequest, getEntityClass());
        return predicate == null ? count() : count(predicate);
    }

    /**
     * Check if any entity exists matching the given QueryRequest
     *
     * @param queryRequest The query request containing conditions
     * @return true if any entity matches the query
     */
    default boolean existsByQueryRequest(QueryRequest queryRequest) {
        Predicate predicate = QueryPredicateBuilder.getInstance()
                .buildPredicate(queryRequest, getEntityClass());
        return predicate == null ? count() > 0 : exists(predicate);
    }

    /**
     * Find all entities matching the given QueryRequest with additional predicates
     * This allows you to combine QueryRequest predicates with custom predicates using BooleanBuilder
     *
     * Example:
     *     BooleanBuilder additionalPredicates = new BooleanBuilder();
     *     if (roleName != null) {
     *         additionalPredicates.and(QUser.user.role.name.eq(roleName));
     *     }
     *     if (loggedInUser != null) {
     *         additionalPredicates.and(QUser.user.createdBy.eq(loggedInUser));
     *     }
     *     return userRepository.findAllByQueryRequestWithAdditionalPredicates(queryRequest, additionalPredicates, pageable);
     *
     * @param queryRequest The query request containing conditions and sorting
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @param pageable Pagination information
     * @return Page of entities matching the query
     */
    default Page<T> findAllByQueryRequestWithAdditionalPredicates(QueryRequest queryRequest,
                                                                  BooleanBuilder additionalPredicates,
                                                                  Pageable pageable) {
        Predicate combinedPredicate = combinePredicates(queryRequest, additionalPredicates);
        return findAll(combinedPredicate, pageable);
    }

    /**
     * Find all entities matching the given QueryRequest with additional predicates (without pagination)
     *
     * @param queryRequest The query request containing conditions and sorting
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @return List of entities matching the query
     */
    default List<T> findAllByQueryRequestWithAdditionalPredicates(QueryRequest queryRequest,
                                                                  BooleanBuilder additionalPredicates) {
        Predicate combinedPredicate = combinePredicates(queryRequest, additionalPredicates);
        return StreamSupport.stream(findAll(combinedPredicate).spliterator(), false).toList();
    }

    /**
     * Count entities matching the given QueryRequest with additional predicates
     *
     * @param queryRequest The query request containing conditions
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @return Number of entities matching the query
     */
    default long countByQueryRequestWithAdditionalPredicates(QueryRequest queryRequest,
                                                             BooleanBuilder additionalPredicates) {
        Predicate combinedPredicate = combinePredicates(queryRequest, additionalPredicates);
        return count(combinedPredicate);
    }

    /**
     * Check if any entity exists matching the given QueryRequest with additional predicates
     *
     * @param queryRequest The query request containing conditions
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @return true if any entity matches the query
     */
    default boolean existsByQueryRequestWithAdditionalPredicates(QueryRequest queryRequest,
                                                                 BooleanBuilder additionalPredicates) {
        Predicate combinedPredicate = combinePredicates(queryRequest, additionalPredicates);
        return exists(combinedPredicate);
    }

    /**
     * Get the entity class for this repository
     * This method should be implemented by concrete repositories
     *
     * @return The entity class
     */
    Class<T> getEntityClass();

    /**
     * Combines QueryRequest predicate with additional predicates using BooleanBuilder
     * This is a helper method to reduce code duplication
     *
     * @param queryRequest The query request containing conditions
     * @param additionalPredicates BooleanBuilder with additional predicates to combine
     * @return Combined predicate, or null if no valid predicates
     */
    default Predicate combinePredicates(QueryRequest queryRequest, BooleanBuilder additionalPredicates) {
        Predicate queryRequestPredicate = QueryPredicateBuilder.getInstance()
                .buildPredicate(queryRequest, getEntityClass());

        BooleanBuilder finalPredicate = new BooleanBuilder();
        if (queryRequestPredicate != null) {
            finalPredicate.and(queryRequestPredicate);
        }
        if (additionalPredicates != null && additionalPredicates.getValue() != null) {
            finalPredicate.and(additionalPredicates);
        }

        return finalPredicate.getValue();
    }
}
