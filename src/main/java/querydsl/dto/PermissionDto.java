package querydsl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDto {

    @NotBlank(message = "Permission name is required")
    @Size(max = 100, message = "Permission name must not exceed 100 characters")
    private String name;

    @Size(max = 200, message = "Resource must not exceed 200 characters")
    private String resource;

    @Size(max = 100, message = "Action must not exceed 100 characters")
    private String action;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Boolean isActive;
}
