package querydsl.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Body of {@code PUT /user/{id}/change-status}.
 *
 * <p>Carries the <em>absolute</em> target state rather than implying a toggle, so the
 * operation is idempotent and free of the concurrent-double-click race the old bodyless
 * toggle had (review finding 4.9).
 */
@Data
public class ChangeUserStatusRequest {

    @NotNull(message = "isActive is required")
    private Boolean isActive;
}
