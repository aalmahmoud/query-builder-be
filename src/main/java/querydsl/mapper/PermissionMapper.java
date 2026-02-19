package querydsl.mapper;

import querydsl.dto.PermissionDto;
import querydsl.dto.PermissionResponseDto;
import querydsl.model.Permission;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public Permission toEntity(PermissionDto permissionDto) {
        if (permissionDto == null) {
            return null;
        }

        Permission permission = new Permission();
        permission.setName(permissionDto.getName());
        permission.setResource(permissionDto.getResource());
        permission.setAction(permissionDto.getAction());
        permission.setDescription(permissionDto.getDescription());
        permission.setIsActive(permissionDto.getIsActive() != null ? permissionDto.getIsActive() : true);

        return permission;
    }

    public PermissionResponseDto toPermissionResponseDto(Permission permission) {
        if (permission == null) {
            return null;
        }

        PermissionResponseDto dto = new PermissionResponseDto();
        dto.setId(permission.getId());
        dto.setName(permission.getName());
        dto.setResource(permission.getResource());
        dto.setAction(permission.getAction());
        dto.setDescription(permission.getDescription());
        dto.setIsActive(permission.getIsActive());
        dto.setCreatedDate(permission.getCreatedDate());
        dto.setLastModifiedDate(permission.getLastModifiedDate());
        dto.setCreatedBy(permission.getCreatedBy());
        dto.setLastModifiedBy(permission.getLastModifiedBy());

        return dto;
    }

    public void updateEntityFromDto(PermissionDto permissionDto, Permission permission) {
        if (permissionDto == null || permission == null) {
            return;
        }

        permission.setName(permissionDto.getName());
        permission.setResource(permissionDto.getResource());
        permission.setAction(permissionDto.getAction());
        permission.setDescription(permissionDto.getDescription());
        if (permissionDto.getIsActive() != null) {
            permission.setIsActive(permissionDto.getIsActive());
        }
    }
}
