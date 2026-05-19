package querydsl.mapper;

import org.springframework.stereotype.Component;
import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.model.Role;
import querydsl.model.User;

/**
 * Pure transformation between {@link User} and its DTOs.
 *
 * <p>Phase 3 fix 3.4: previously this mapper called {@code roleRepository.findById}
 * to resolve a role-by-id, which made it impossible to unit-test without booting a
 * repository and entangled persistence with mapping. The service layer
 * ({@link querydsl.service.UserService}) now resolves the {@link Role} and passes
 * it in directly.
 */
@Component
public class UserMapper {

    public User toEntity(UserDto userDto, Role resolvedRole) {
        if (userDto == null) {
            return null;
        }
        User user = new User();
        copyEditableFields(userDto, user);
        user.setIsActive(userDto.getIsActive() != null ? userDto.getIsActive() : true);
        user.setRole(resolvedRole);
        return user;
    }

    public UserResponseDto toUserResponseDto(User user) {
        if (user == null) {
            return null;
        }

        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setMobileNumber(user.getMobileNumber());
        dto.setNationalId(user.getNationalId());
        dto.setIsActive(user.getIsActive());
        dto.setCreatedDate(user.getCreatedDate());
        dto.setLastModifiedDate(user.getLastModifiedDate());
        dto.setCreatedBy(user.getCreatedBy());
        dto.setLastModifiedBy(user.getLastModifiedBy());

        if (user.getRole() != null) {
            dto.setRoleId(user.getRole().getId());
            dto.setRoleName(user.getRole().getName());
        }

        return dto;
    }

    public void updateEntityFromDto(UserDto userDto, User user, Role resolvedRole) {
        if (userDto == null || user == null) {
            return;
        }
        copyEditableFields(userDto, user);
        if (userDto.getIsActive() != null) {
            user.setIsActive(userDto.getIsActive());
        }
        if (resolvedRole != null) {
            user.setRole(resolvedRole);
        }
    }

    private static void copyEditableFields(UserDto src, User dest) {
        dest.setFirstName(src.getFirstName());
        dest.setLastName(src.getLastName());
        dest.setEmail(src.getEmail());
        dest.setMobileNumber(src.getMobileNumber());
        dest.setNationalId(src.getNationalId());
    }
}
