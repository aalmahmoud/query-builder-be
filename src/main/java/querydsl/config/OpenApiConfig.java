package querydsl.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Generic QueryDSL Builder API")
                        .version("1.0.0")
                        .description("""
                                A generic QueryDSL system for Spring Boot that enables dynamic querying \
                                through JSON request bodies instead of URL parameters.
                                
                                ## Features
                                - Dynamic queries via JSON request body with 20+ operations
                                - Nested field navigation (dot-notation) and computed fields
                                - Type-safe with QueryDSL, works with any JPA entity
                                - Excel and PDF export with column selection
                                - JWT authentication with role-based access control
                                
                                ## Authentication
                                Use `POST /auth/login` to obtain a JWT token, then include it as \
                                `Authorization: Bearer <token>` in all subsequent requests.
                                """)
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter the JWT token obtained from /auth/login")));
    }
}
