package querydsl.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import querydsl.export.Exportable;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Exportable(fields = {
        "id", "firstName", "lastName", "email", "mobileNumber", "nationalId", "isActive",
        "createdDate", "lastModifiedDate", "createdBy", "lastModifiedBy",
        "role.id", "role.name"
})
public class User extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(length = 20)
    private String mobileNumber;

    @Column(nullable = false, unique = true, length = 50)
    private String nationalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(length = 255)
    private String password;
}
