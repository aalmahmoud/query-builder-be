package querydsl.mapper;

import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.model.Role;
import querydsl.model.User;
import querydsl.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final RoleRepository roleRepository;

    public User toEntity(UserDto userDto) {
        if (userDto == null) {
            return null;
        }

        User user = new User();
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setEmail(userDto.getEmail());
        user.setMobileNumber(userDto.getMobileNumber());
        user.setNationalId(userDto.getNationalId());
        user.setIsActive(userDto.getIsActive() != null ? userDto.getIsActive() : true);
        resolveRole(userDto.getRoleId(), user);
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

    public void updateEntityFromDto(UserDto userDto, User user) {
        if (userDto == null || user == null) {
            return;
        }

        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setEmail(userDto.getEmail());
        user.setMobileNumber(userDto.getMobileNumber());
        user.setNationalId(userDto.getNationalId());
        if (userDto.getIsActive() != null) {
            user.setIsActive(userDto.getIsActive());
        }
        resolveRole(userDto.getRoleId(), user);
    }

    private void resolveRole(Long roleId, User user) {
        if (roleId != null) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new EntityNotFoundException("Role", roleId));
            user.setRole(role);
        }
    }
}
