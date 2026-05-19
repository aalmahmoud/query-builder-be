package querydsl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Phase 5 fix 5.28: scans for entities and repositories are pinned to explicit packages
 * rather than relying on the default {@code @SpringBootApplication} package detection.
 * Necessary because some persistence types now live in the {@code :generic-querydsl}
 * subproject; the explicit scan keeps both modules wired regardless of where the
 * main class moves to.
 */
@SpringBootApplication
@EntityScan(basePackages = "querydsl.model")
@EnableJpaRepositories(basePackages = "querydsl.repository")
public class QuerydslbuilderApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuerydslbuilderApplication.class, args);
    }

}
