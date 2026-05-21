package querydsl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import querydsl.model.SavedQuery;

import java.util.List;
import java.util.Optional;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    List<SavedQuery> findByEntityNameAndCreatedByOrderByCreatedDateDesc(String entityName, String createdBy);

    Optional<SavedQuery> findByIdAndEntityNameAndCreatedBy(Long id, String entityName, String createdBy);
}
