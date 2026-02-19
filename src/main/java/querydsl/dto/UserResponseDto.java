package querydsl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String mobileNumber;
    private String nationalId;
    private Long roleId;
    private String roleName;
    private Boolean isActive;
    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;
    private String createdBy;
    private String lastModifiedBy;
}
