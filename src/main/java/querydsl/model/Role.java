package querydsl.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import querydsl.export.Exportable;
import querydsl.query.SortableFields;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Exportable(fields = {
        "id", "name", "description", "isActive",
        "createdDate", "lastModifiedDate", "createdBy", "lastModifiedBy"
})
@SortableFields({
        "id", "name", "isActive", "createdDate", "lastModifiedDate"
})
public class Role extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    @EqualsAndHashCode.Include
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean isActive;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();
}
