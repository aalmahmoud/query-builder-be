package querydsl.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import querydsl.dto.validation.OnCreate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 200, message = "Email must not exceed 200 characters")
    private String email;

    @Pattern(regexp = "^$|^\\+?[0-9 \\-]{6,20}$",
            message = "Mobile number must be 6-20 digits (optionally with +, spaces, dashes)")
    @Size(max = 20, message = "Mobile number must not exceed 20 characters")
    private String mobileNumber;

    @NotBlank(message = "National ID is required")
    @Size(max = 50, message = "National ID must not exceed 50 characters")
    private String nationalId;

    private Long roleId;

    // Phase 4 fix 4.10: enforce length and one-letter-one-digit composition *on create only*.
    // PUT /user/{id} continues to accept a null/empty password (means "leave unchanged"),
    // so the OnCreate group is only activated by POST /user.
    @NotBlank(message = "Password is required", groups = OnCreate.class)
    @Size(min = 8, max = 128,
            message = "Password must be 8-128 characters", groups = OnCreate.class)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Password must contain at least one letter and one digit",
            groups = OnCreate.class)
    private String password;

    private Boolean isActive;
}
