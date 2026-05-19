package querydsl.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import querydsl.model.User;

import java.util.Optional;

/**
 * Generic User Repository extending GenericQueryRepository
 * Provides full search capabilities through the generic QueryDSL system
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, GenericQueryRepository<User, Long> {

    @Override
    default Class<User> getEntityClass() {
        return User.class;
    }

    /**
     * Phase 4 fix 4.15: lookup by national ID goes through the deterministic HMAC
     * companion column. Call sites compute the hash via {@code EncryptionService.hmac}
     * before invoking this. The plaintext column is encrypted with a per-row IV and
     * cannot be queried directly.
     */
    Optional<User> findByNationalIdHash(String nationalIdHash);

    /**
     * Eagerly fetches role + permissions so that {@code CustomUserDetailsService}
     * can build authorities without triggering lazy-load N+1 queries (or
     * {@code LazyInitializationException} once OSIV is disabled in production).
     */
    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findByEmail(String email);

    /**
     * Phase 3 fix 3.5: paginated user list eagerly joins {@code role} so the response
     * mapper does not trigger one extra query per row (N+1) when populating
     * {@code roleName}. The query-DSL paths inherit the same problem and should be
     * fixed at the engine layer in a later phase.
     */
    @Override
    @EntityGraph(attributePaths = "role")
    Page<User> findAll(Pageable pageable);
}
