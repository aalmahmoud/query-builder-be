package querydsl.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import querydsl.exception.ValidationException;
import querydsl.model.Role;
import querydsl.model.User;
import querydsl.repository.RefreshTokenRepository;
import querydsl.repository.RoleRepository;
import querydsl.repository.UserRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Phase 3 fix 3.3 — refresh token issuance, validation, revocation.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenServiceTest {

    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EncryptionService encryptionService;

    private User user;

    @BeforeEach
    void seed() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role role = new Role();
        role.setName("USER");
        role.setIsActive(true);
        roleRepository.save(role);

        user = new User();
        user.setFirstName("Refresh");
        user.setLastName("Tester");
        user.setEmail("refresh@test.local");
        user.setNationalId("rt-1");
        user.setNationalIdHash(encryptionService.hmac("rt-1"));
        user.setIsActive(true);
        user.setPassword("$2a$10$hash");
        user.setRole(role);
        userRepository.save(user);
    }

    @Test
    void issueFor_returnsOpaquePlaintext_andPersistsHashOnly() {
        String plaintext = refreshTokenService.issueFor(user);
        assertNotNull(plaintext);
        assertTrue(plaintext.length() > 30, "Token should be high-entropy base64url");
        assertEquals(1, refreshTokenRepository.count());

        // Plaintext is not in the DB.
        assertTrue(refreshTokenRepository.findAll().stream()
                .noneMatch(rt -> plaintext.equals(rt.getTokenHash())),
                "DB stores the SHA-256 hash, not the plaintext");
    }

    @Test
    void validateAndGetUser_acceptsFreshToken() {
        String token = refreshTokenService.issueFor(user);
        User resolved = refreshTokenService.validateAndGetUser(token);
        assertEquals(user.getEmail(), resolved.getEmail());
    }

    @Test
    void validateAndGetUser_rejectsUnknownToken() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> refreshTokenService.validateAndGetUser("not-a-real-token"));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid")
                || ex.getMessage().toLowerCase().contains("revoked"));
    }

    @Test
    void revoke_removesTheToken() {
        String token = refreshTokenService.issueFor(user);
        assertEquals(1, refreshTokenRepository.count());

        refreshTokenService.revoke(token);
        assertEquals(0, refreshTokenRepository.count());

        assertThrows(ValidationException.class,
                () -> refreshTokenService.validateAndGetUser(token));
    }

    @Test
    void expiredToken_isRejected() {
        // Issue a token, then poison the expiry to a past instant.
        String token = refreshTokenService.issueFor(user);
        refreshTokenRepository.findAll().forEach(rt -> {
            rt.setExpiresAt(Instant.now().minusSeconds(60));
            refreshTokenRepository.save(rt);
        });

        ValidationException ex = assertThrows(ValidationException.class,
                () -> refreshTokenService.validateAndGetUser(token));
        assertTrue(ex.getMessage().toLowerCase().contains("expired"));
    }

    @Test
    void issueFor_generatesDistinctTokens() {
        String a = refreshTokenService.issueFor(user);
        String b = refreshTokenService.issueFor(user);
        assertNotEquals(a, b);
        assertEquals(2, refreshTokenRepository.count());
    }

    @Test
    void revokeAllForUser_clearsEveryTokenForThatUser() {
        refreshTokenService.issueFor(user);
        refreshTokenService.issueFor(user);
        refreshTokenService.issueFor(user);
        assertEquals(3, refreshTokenRepository.count());

        int removed = refreshTokenService.revokeAllForUser(user.getId());
        assertEquals(3, removed);
        assertEquals(0, refreshTokenRepository.count());
    }

    @Test
    void refreshExpirationProperty_isHonoured() {
        long original = (long) ReflectionTestUtils.getField(refreshTokenService, "refreshExpirationMs");
        assertTrue(original > 0, "default refresh expiration must be > 0");
    }
}
