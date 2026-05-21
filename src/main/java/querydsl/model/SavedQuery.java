package querydsl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A persisted, named {@code QueryRequest}, scoped per entity and per owner (createdBy).
 * The request itself is stored as JSON in {@link #queryJson}.
 */
@Entity
@Table(name = "saved_queries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SavedQuery extends BaseEntity {

    /** Which entity this query targets: {@code user}, {@code role} or {@code permission}. */
    @Column(name = "entity_name", nullable = false, length = 50)
    private String entityName;

    @Column(nullable = false, length = 150)
    private String name;

    /** The serialized QueryRequest JSON. */
    @Column(name = "query_json", nullable = false, length = 4000)
    private String queryJson;
}
