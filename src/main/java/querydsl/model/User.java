package querydsl.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import querydsl.export.Exportable;
import querydsl.query.SortableFields;
import querydsl.security.EncryptedStringConverter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Exportable(fields = {
        "id", "firstName", "lastName", "email", "mobileNumber", "nationalId", "isActive",
        "createdDate", "lastModifiedDate", "createdBy", "lastModifiedBy",
        "role.id", "role.name"
})
@SortableFields({
        "id", "firstName", "lastName", "email", "isActive",
        "createdDate", "lastModifiedDate", "role.name"
})
public class User extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 200)
    @EqualsAndHashCode.Include
    private String email;

    @Column(length = 20)
    private String mobileNumber;

    /**
     * Phase 4 fix 4.15: national ID is PII; AES-256-GCM encrypted at rest via
     * {@link EncryptedStringConverter}. The unique constraint moved off this column
     * (ciphertext is non-deterministic) onto the companion {@link #nationalIdHash}.
     * Increased to 500 chars to hold IV + ciphertext + base64 overhead.
     */
    @Column(nullable = false, length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String nationalId;

    /**
     * Deterministic HMAC-SHA256 of {@link #nationalId}, used to enforce uniqueness and
     * support direct equality lookup. Populated by the service layer on add/update —
     * see {@code UserService}.
     */
    @Column(name = "national_id_hash", nullable = false, unique = true, length = 64)
    private String nationalIdHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(length = 255)
    private String password;
}
