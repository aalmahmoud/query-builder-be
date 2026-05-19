package querydsl.repository;

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

    Optional<User> findByNationalId(String nationalId);

    /**
     * Eagerly fetches role + permissions so that {@code CustomUserDetailsService}
     * can build authorities without triggering lazy-load N+1 queries (or
     * {@code LazyInitializationException} once OSIV is disabled in production).
     */
    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findByEmail(String email);
}
