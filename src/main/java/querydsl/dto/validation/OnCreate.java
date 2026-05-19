package querydsl.dto.validation;

/**
 * Validation group activated when creating a resource ({@code POST}). Lets a shared DTO
 * enforce stricter rules on create than on update — used by {@link querydsl.dto.UserDto}'s
 * password constraints (Phase 4 fix 4.10).
 */
public interface OnCreate {
}
