package querydsl.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for Phase 3 fix 3.1.
 *
 * <p>Previously {@code @EnableJpaAuditing} had no {@link AuditorAware} provider, so
 * {@code BaseEntity.createdBy} / {@code lastModifiedBy} were always null. The bean now
 * returns the authenticated principal's name, falling back to {@code "system"}.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditorAwareTest {

    @Autowired
    private AuditorAware<String> auditorAware;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticated_returnsSystem() {
        SecurityContextHolder.clearContext();
        Optional<String> auditor = auditorAware.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }

    @Test
    void authenticatedUser_returnsTheirName() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "alice@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<String> auditor = auditorAware.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("alice@example.com", auditor.get());
    }
}
