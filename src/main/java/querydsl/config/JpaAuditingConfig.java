package querydsl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Enables JPA Auditing and supplies the {@link AuditorAware} that populates
 * {@code createdBy} / {@code lastModifiedBy} on every {@code BaseEntity}.
 *
 * <p>Phase 3 fix 3.1: previously {@code @EnableJpaAuditing} was declared with no
 * auditor provider, so audit columns were always {@code null}. Now the authenticated
 * principal's name (the user email, from the JWT subject claim) is recorded for
 * any HTTP-driven write; background work without an authentication context gets
 * {@code "system"}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    private static final String SYSTEM_PRINCIPAL = "system";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null
                    || !auth.isAuthenticated()
                    || auth instanceof AnonymousAuthenticationToken) {
                return Optional.of(SYSTEM_PRINCIPAL);
            }
            return Optional.of(auth.getName());
        };
    }
}
