package querydsl.mapper;

import org.springframework.stereotype.Component;
import querydsl.dto.RoleDto;
import querydsl.dto.RoleResponseDto;
import querydsl.model.Permission;
import querydsl.model.Role;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure transformation between {@link Role} and its DTOs.
 *
 * <p>Phase 3 fix 3.4: permission resolution moved out of the mapper into
 * {@link querydsl.service.RoleService}.
 */
@Component
public class RoleMapper {

    public Role toEntity(RoleDto roleDto, Set<Permission> resolvedPermissions) {
        if (roleDto == null) {
            return null;
        }
        Role role = new Role();
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setIsActive(roleDto.getIsActive() != null ? roleDto.getIsActive() : true);
        role.setPermissions(resolvedPermissions != null ? resolvedPermissions : Set.of());
        return role;
    }

    public RoleResponseDto toRoleResponseDto(Role role) {
        if (role == null) {
            return null;
        }

        RoleResponseDto dto = new RoleResponseDto();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setIsActive(role.getIsActive());
        dto.setCreatedDate(role.getCreatedDate());
        dto.setLastModifiedDate(role.getLastModifiedDate());
        dto.setCreatedBy(role.getCreatedBy());
        dto.setLastModifiedBy(role.getLastModifiedBy());

        if (role.getPermissions() != null) {
            dto.setPermissionIds(role.getPermissions().stream()
                    .map(Permission::getId)
                    .collect(Collectors.toSet()));
            dto.setPermissionNames(role.getPermissions().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toSet()));
        } else {
            dto.setPermissionIds(Set.of());
            dto.setPermissionNames(Set.of());
        }

        return dto;
    }

    public void updateEntityFromDto(RoleDto roleDto, Role role, Set<Permission> resolvedPermissions) {
        if (roleDto == null || role == null) {
            return;
        }
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        if (roleDto.getIsActive() != null) {
            role.setIsActive(roleDto.getIsActive());
        }
        // permissionIds == null on input means "leave unchanged"; an empty/non-null set replaces.
        if (roleDto.getPermissionIds() != null) {
            role.setPermissions(resolvedPermissions != null ? resolvedPermissions : Set.of());
        }
    }
}
