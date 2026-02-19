package querydsl.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Size(max = 20, message = "Mobile number must not exceed 20 characters")
    private String mobileNumber;

    @NotBlank(message = "National ID is required")
    @Size(max = 50, message = "National ID must not exceed 50 characters")
    private String nationalId;

    private Long roleId;

    private String password;

    private Boolean isActive;
}
