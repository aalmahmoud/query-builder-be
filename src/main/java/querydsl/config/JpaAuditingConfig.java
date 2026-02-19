package querydsl.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration to enable JPA Auditing
 * This enables @CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy annotations
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
