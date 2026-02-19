package querydsl.repository;

import querydsl.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Generic Role Repository extending GenericQueryRepository
 * Provides full search capabilities through the generic QueryDSL system
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long>, GenericQueryRepository<Role, Long> {
    
    @Override
    default Class<Role> getEntityClass() {
        return Role.class;
    }
}
