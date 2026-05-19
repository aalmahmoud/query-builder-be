package querydsl.mapper;

import org.junit.jupiter.api.Test;
import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.model.Role;
import querydsl.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for Phase 3 fix 3.4.
 *
 * <p>Mappers no longer reach into repositories; resolution moved into the service layer.
 * That means this test can run without any Spring infrastructure, repositories, or mocks.
 */
class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void toEntity_withResolvedRole_setsRole() {
        UserDto dto = new UserDto();
        dto.setFirstName("Jane");
        dto.setLastName("Doe");
        dto.setEmail("jane@example.com");
        dto.setNationalId("1");

        Role role = new Role();
        role.setName("ADMIN");

        User out = mapper.toEntity(dto, role);

        assertEquals("Jane", out.getFirstName());
        assertEquals(role, out.getRole());
        assertTrue(out.getIsActive(), "default isActive should be true when DTO does not specify");
    }

    @Test
    void toEntity_withNullRole_setsRoleNull() {
        UserDto dto = new UserDto();
        dto.setFirstName("Jane");
        dto.setLastName("Doe");
        dto.setEmail("jane@example.com");
        dto.setNationalId("1");

        User out = mapper.toEntity(dto, null);

        assertNull(out.getRole());
    }

    @Test
    void toResponseDto_populatesRoleFields() {
        Role role = new Role();
        role.setName("ADMIN");
        // id is normally set by JPA; we leave it null here.

        User user = new User();
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setEmail("jane@example.com");
        user.setRole(role);

        UserResponseDto dto = mapper.toUserResponseDto(user);

        assertEquals("Jane", dto.getFirstName());
        assertEquals("ADMIN", dto.getRoleName());
    }
}
