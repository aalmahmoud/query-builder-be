package querydsl.mapper;

import querydsl.dto.RoleDto;
import querydsl.dto.RoleResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.model.Permission;
import querydsl.model.Role;
import querydsl.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoleMapper {

    private final PermissionRepository permissionRepository;

    public Role toEntity(RoleDto roleDto) {
        if (roleDto == null) {
            return null;
        }

        Role role = new Role();
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setIsActive(roleDto.getIsActive() != null ? roleDto.getIsActive() : true);
        role.setPermissions(resolvePermissions(roleDto.getPermissionIds()));
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

    public void updateEntityFromDto(RoleDto roleDto, Role role) {
        if (roleDto == null || role == null) {
            return;
        }

        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        if (roleDto.getIsActive() != null) {
            role.setIsActive(roleDto.getIsActive());
        }
        if (roleDto.getPermissionIds() != null) {
            role.setPermissions(resolvePermissions(roleDto.getPermissionIds()));
        }
    }

    private Set<Permission> resolvePermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        return permissionIds.stream()
                .map(id -> permissionRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Permission", id)))
                .collect(Collectors.toSet());
    }
}
