package querydsl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponseDto {

    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private Set<Long> permissionIds;
    private Set<String> permissionNames;
    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;
    private String createdBy;
    private String lastModifiedBy;
}
