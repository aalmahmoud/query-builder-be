package querydsl.repository;

import querydsl.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    Optional<User> findByEmail(String email);
}
