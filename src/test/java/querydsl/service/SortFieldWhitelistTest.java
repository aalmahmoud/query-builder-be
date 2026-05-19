package querydsl.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import querydsl.exception.QueryException;
import querydsl.query.QueryRequest;
import querydsl.query.SortField;
import querydsl.repository.GenericQueryRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for Phase 4 fix 4.2 — sort field whitelist.
 *
 * <p>The {@code GenericQueryService} now rejects sort fields not listed in the entity's
 * {@link querydsl.query.SortableFields} annotation, instead of trusting client input.
 *
 * <p>Tested without booting Spring: we drive the service with a mocked
 * {@code GenericQueryRepository} and a real {@code GenericQueryService}, then check
 * that a known-bad sort field throws.
 */
class SortFieldWhitelistTest {

    @Test
    void unknownSortField_isRejected() {
        GenericQueryService service = new GenericQueryService();

        @SuppressWarnings("unchecked")
        GenericQueryRepository<querydsl.model.User, Long> repo = mock(GenericQueryRepository.class);
        when(repo.getEntityClass()).thenReturn(querydsl.model.User.class);

        QueryRequest request = new QueryRequest();
        request.setSortFields(List.of(new SortField("role.permissions.id", SortField.SortDirection.ASC)));

        Pageable pageable = PageRequest.of(0, 10);

        QueryException ex = assertThrows(QueryException.class,
                () -> service.findAllByQueryRequest(repo, request, pageable));
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("role.permissions.id"),
                "Error must name the rejected field. Got: " + ex.getMessage());
    }

    @Test
    void allowedSortField_passesThrough() {
        GenericQueryService service = new GenericQueryService();

        @SuppressWarnings("unchecked")
        GenericQueryRepository<querydsl.model.User, Long> repo = mock(GenericQueryRepository.class);
        when(repo.getEntityClass()).thenReturn(querydsl.model.User.class);

        QueryRequest request = new QueryRequest();
        request.setSortFields(List.of(new SortField("email", SortField.SortDirection.ASC)));

        Pageable pageable = PageRequest.of(0, 10);

        // The service is invoked; repo returns null by default which is fine for the assertion.
        service.findAllByQueryRequest(repo, request, pageable);
        // No exception thrown == pass.
        org.mockito.Mockito.verify(repo).findAllByQueryRequest(any(), any());
    }
}
