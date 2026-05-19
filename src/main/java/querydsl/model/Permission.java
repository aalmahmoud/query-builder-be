package querydsl.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import querydsl.export.Exportable;
import querydsl.query.SortableFields;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Exportable(fields = {
        "id", "name", "resource", "action", "description", "isActive",
        "createdDate", "lastModifiedDate", "createdBy", "lastModifiedBy"
})
@SortableFields({
        "id", "name", "resource", "action", "isActive", "createdDate", "lastModifiedDate"
})
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 200)
    private String resource;

    @Column(length = 100)
    private String action;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean isActive = true;
}
