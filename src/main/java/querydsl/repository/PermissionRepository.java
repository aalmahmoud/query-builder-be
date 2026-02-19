package querydsl.repository;

import querydsl.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Generic Permission Repository extending GenericQueryRepository
 * Provides full search capabilities through the generic QueryDSL system
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long>, GenericQueryRepository<Permission, Long> {
    
    @Override
    default Class<Permission> getEntityClass() {
        return Permission.class;
    }
}
