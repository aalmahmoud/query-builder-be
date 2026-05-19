package querydsl.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import querydsl.exception.ValidationException;
import querydsl.model.RefreshToken;
import querydsl.model.User;
import querydsl.repository.RefreshTokenRepository;
import querydsl.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Phase 3 fix 3.3.
 *
 * <p>Issues, validates, and revokes refresh tokens. The plaintext token is opaque random
 * (32 bytes, base64url-encoded). We store SHA-256 of the plaintext so DB compromise does
 * not directly leak usable tokens.
 *
 * <p>Default lifetime: 7 days, override via {@code jwt.refresh-expiration} (milliseconds).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days default
    private long refreshExpirationMs;

    /**
     * Issues a new refresh token for {@code user}. Returns the plaintext token; only the
     * SHA-256 hash is persisted.
     */
    @Transactional
    public String issueFor(User user) {
        String plaintext = generatePlaintextToken();
        String hash = sha256Hex(plaintext);

        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setTokenHash(hash);
        record.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        record.setCreatedAt(Instant.now());
        refreshTokenRepository.save(record);

        log.debug("Issued refresh token for user {}", user.getEmail());
        return plaintext;
    }

    /**
     * Validates the plaintext token and returns the owning user. Used by {@code /auth/refresh}.
     *
     * @throws ValidationException if the token is unknown, revoked (deleted), or expired.
     */
    @Transactional(readOnly = true)
    public User validateAndGetUser(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) {
            throw new ValidationException("Refresh token is required");
        }
        String hash = sha256Hex(plaintextToken);
        RefreshToken record = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ValidationException("Refresh token is invalid or has been revoked"));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("Refresh token has expired");
        }
        return userRepository.findById(record.getUserId())
                .orElseThrow(() -> new ValidationException("Refresh token references a missing user"));
    }

    /** Revokes a single refresh token (logout for this device). */
    @Transactional
    public void revoke(String plaintextToken) {
        if (plaintextToken == null || plaintextToken.isBlank()) {
            return;
        }
        int deleted = refreshTokenRepository.deleteByTokenHash(sha256Hex(plaintextToken));
        if (deleted > 0) {
            log.debug("Revoked 1 refresh token");
        }
    }

    /** Revokes every refresh token for a user (logout from all devices, e.g. on password change). */
    @Transactional
    public int revokeAllForUser(Long userId) {
        int deleted = refreshTokenRepository.deleteAllByUserId(userId);
        log.info("Revoked {} refresh token(s) for user {}", deleted, userId);
        return deleted;
    }

    private static String generatePlaintextToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns the SHA-256 hex digest of the input. Refresh tokens are high-entropy random; */
    /* a fast digest is appropriate (bcrypt would be overkill and slow). */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }
}
