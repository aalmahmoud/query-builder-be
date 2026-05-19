package querydsl.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body of {@code POST /auth/refresh} and {@code POST /auth/logout}.
 * Carries the plaintext refresh token previously issued at login.
 */
@Data
public class RefreshRequest {
    @NotBlank(message = "refreshToken is required")
    private String refreshToken;
}
