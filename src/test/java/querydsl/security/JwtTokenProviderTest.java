package querydsl.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for JwtTokenProvider.
 *
 * <p>Phase 1 fix 1.4: hard-coded JWT secret default removed. Application must fail to start
 * if JWT_SECRET is unset or shorter than 32 bytes (256 bits required for HS256).
 */
class JwtTokenProviderTest {

    @Test
    void validateSecret_rejectsNullSecret() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::validateSecret);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void validateSecret_rejectsShortSecret() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "too-short");

        assertThrows(IllegalStateException.class, provider::validateSecret);
    }

    @Test
    void validateSecret_rejectsExactlyShortOfMinimum() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "a".repeat(31));

        assertThrows(IllegalStateException.class, provider::validateSecret);
    }

    @Test
    void validateSecret_acceptsAtMinimum() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "a".repeat(32));

        assertDoesNotThrow(provider::validateSecret);
    }

    @Test
    void validateSecret_acceptsLongSecret() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret",
                "Y4sLZeQyOQg2Q8FaBJh3CzJQ0wMl9p1H5dWxQy5xkRk9bxqJqgUq+wqRhCmnCt7L");

        assertDoesNotThrow(provider::validateSecret);
    }
}
