package querydsl.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import querydsl.query.QueryPredicateBuilder;
import querydsl.query.computed.ComputedFieldHandlerRegistry;
import querydsl.service.GenericQueryService;

/**
 * Spring Boot auto-configuration for the generic-querydsl library.
 *
 * <p>Component-scans the library's packages so that consumers do not need to add the
 * library to their own {@code @SpringBootApplication}'s scanBasePackages.
 *
 * <p>This auto-config is wired through {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * so it is picked up automatically when the library is on the classpath.
 *
 * @see QueryPredicateBuilder
 * @see ComputedFieldHandlerRegistry
 * @see GenericQueryService
 */
@AutoConfiguration
@ComponentScan(basePackageClasses = {
        QueryPredicateBuilder.class,
        ComputedFieldHandlerRegistry.class,
        GenericQueryService.class
})
public class GenericQuerydslAutoConfiguration {
}
