package querydsl.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import querydsl.dto.PermissionDto;
import querydsl.dto.RoleDto;
import querydsl.dto.UserDto;
import querydsl.mapper.PermissionMapper;
import querydsl.mapper.RoleMapper;
import querydsl.mapper.UserMapper;
import querydsl.model.Permission;
import querydsl.model.Role;
import querydsl.model.User;
import querydsl.repository.PermissionRepository;
import querydsl.repository.RoleRepository;
import querydsl.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Regression tests for Phase 1 fix 1.5: write operations on UserService / RoleService /
 * PermissionService now require ROLE_ADMIN via {@code @PreAuthorize}.
 *
 * <p>Previously the only authorization was URL-prefix based (configured in SecurityConfig),
 * which let a USER-role caller invoke {@code DELETE /user/{adminId}} and disable any user
 * including the admin.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthorizationTest {

    @Autowired private UserService userService;
    @Autowired private RoleService roleService;
    @Autowired private PermissionService permissionService;

    @MockBean private UserRepository userRepository;
    @MockBean private RoleRepository roleRepository;
    @MockBean private PermissionRepository permissionRepository;
    @MockBean private UserMapper userMapper;
    @MockBean private RoleMapper roleMapper;
    @MockBean private PermissionMapper permissionMapper;

    // ---- UserService ----

    @Test
    @WithMockUser(roles = "USER")
    void user_cannotAddUser() {
        assertThrows(AccessDeniedException.class, () -> userService.addUser(new UserDto()));
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannotUpdateUser() {
        assertThrows(AccessDeniedException.class, () -> userService.updateUser(1L, new UserDto()));
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannotChangeUserStatus() {
        assertThrows(AccessDeniedException.class, () -> userService.changeUserStatus(1L));
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_cannotDeleteUser() {
        assertThrows(AccessDeniedException.class, () -> userService.deleteUser(1L));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void manager_cannotDeleteUser() {
        // Previously /user/** allowed MANAGER. Method-level authz now narrows it.
        assertThrows(AccessDeniedException.class, () -> userService.deleteUser(1L));
    }

    @Test
    @WithAnonymousUser
    void anonymous_cannotAddUser() {
        assertThrows(AccessDeniedException.class, () -> userService.addUser(new UserDto()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canAddUser() {
        Mockito.when(userMapper.toEntity(any(UserDto.class), eq(null))).thenReturn(new User());
        userService.addUser(new UserDto());
        Mockito.verify(userRepository).save(any());
    }

    // ---- RoleService ----

    @Test
    @WithMockUser(roles = "MANAGER")
    void manager_cannotAddRole() {
        // SecurityConfig allows /role/** for MANAGER+ADMIN; the method-level rule narrows to ADMIN.
        assertThrows(AccessDeniedException.class, () -> roleService.addRole(new RoleDto()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canAddRole() {
        Mockito.when(roleMapper.toEntity(any(RoleDto.class), any())).thenReturn(new Role());
        roleService.addRole(new RoleDto());
        Mockito.verify(roleRepository).save(any());
    }

    // ---- PermissionService ----

    @Test
    @WithMockUser(roles = "MANAGER")
    void manager_cannotAddPermission() {
        assertThrows(AccessDeniedException.class,
                () -> permissionService.addPermission(new PermissionDto()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canAddPermission() {
        Mockito.when(permissionMapper.toEntity(any())).thenReturn(new Permission());
        permissionService.addPermission(new PermissionDto());
        Mockito.verify(permissionRepository).save(any());
    }
}
