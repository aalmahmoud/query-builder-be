package querydsl.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
