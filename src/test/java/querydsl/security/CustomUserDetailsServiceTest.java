package querydsl.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import querydsl.model.Permission;
import querydsl.model.Role;
import querydsl.model.User;
import querydsl.repository.PermissionRepository;
import querydsl.repository.RoleRepository;
import querydsl.repository.UserRepository;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for Phase 1 fix 1.6.
 *
 * <p>{@code CustomUserDetailsService.loadUserByUsername} previously traversed lazy collections
 * ({@code user.role.permissions}) outside any transaction. It only worked because Spring Boot
 * enables OSIV by default. With {@code spring.jpa.open-in-view=false} (the prod-correct setting,
 * applied via {@code application-test.properties}), the bug would surface as a
 * {@code LazyInitializationException}.
 *
 * <p>Fix: {@code @Transactional(readOnly = true)} on the method plus
 * {@code @EntityGraph(attributePaths = {"role", "role.permissions"})} on
 * {@code UserRepository.findByEmail}.
 */
@SpringBootTest
@ActiveProfiles("test")
class CustomUserDetailsServiceTest {

    @Autowired private CustomUserDetailsService service;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;

    @BeforeEach
    void wipe() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    @Transactional
    void loadsUserAndPermissions_withOsivDisabled() {
        Permission perm = new Permission();
        perm.setName("user:read");
        perm.setResource("user");
        perm.setAction("read");
        perm.setIsActive(true);
        permissionRepository.save(perm);

        Role role = new Role();
        role.setName("ADMIN");
        role.setIsActive(true);
        role.setPermissions(Set.of(perm));
        roleRepository.save(role);

        User user = new User();
        user.setFirstName("Test");
        user.setLastName("Admin");
        user.setEmail("admin@test.local");
        user.setNationalId("999");
        user.setIsActive(true);
        user.setPassword("$2a$10$irrelevant");
        user.setRole(role);
        userRepository.save(user);

        UserDetails details = service.loadUserByUsername("admin@test.local");

        assertEquals("admin@test.local", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                "Expected ROLE_ADMIN authority. Got: " + details.getAuthorities());
        assertTrue(details.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("user:read")),
                "Expected user:read permission authority. Got: " + details.getAuthorities());
    }

    @Test
    void unknownEmail_throwsUsernameNotFound() {
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("nobody@test.local"));
    }
}
