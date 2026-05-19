package querydsl.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Column-level encryption + deterministic HMAC for PII fields (Phase 4 fix 4.15).
 *
 * <p>Two operations:
 * <ul>
 *   <li>{@link #encrypt}/{@link #decrypt} — AES-256-GCM with a random 12-byte IV per
 *       operation. Ciphertext format on disk: base64({iv (12 bytes)} || {gcm output}).
 *       Different ciphertexts for the same plaintext, so the column is NOT directly
 *       queryable by equality.</li>
 *   <li>{@link #hmac} — HMAC-SHA256 over the input, hex-encoded. Deterministic so the
 *       same plaintext always produces the same digest, suitable for a unique index
 *       and equality lookup.</li>
 * </ul>
 *
 * <p>The key is loaded from the {@code APP_ENCRYPTION_KEY} environment variable
 * (base64-encoded 32 bytes for AES-256). The same key is used for both AES and HMAC;
 * realistic deployments derive separate sub-keys per purpose, but the threat model
 * here (single-tenant, single-purpose) does not warrant the extra ceremony.
 *
 * <p>Boot fails ({@code IllegalStateException} in {@code @PostConstruct}) if the key
 * is missing or wrong-length, so misconfiguration cannot reach the data path.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final int AES_KEY_BYTES = 32;       // AES-256
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String AES_ALG = "AES/GCM/NoPadding";
    private static final String HMAC_ALG = "HmacSHA256";

    private static final SecureRandom RNG = new SecureRandom();

    @Value("${app.encryption.key:}")
    private String keyB64;

    private byte[] keyBytes;
    private SecretKeySpec aesKey;
    private SecretKeySpec hmacKey;

    @PostConstruct
    void init() {
        if (keyB64 == null || keyB64.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.key must be set via the APP_ENCRYPTION_KEY environment "
                            + "variable. Generate with: openssl rand -base64 32");
        }
        try {
            keyBytes = Base64.getDecoder().decode(keyB64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be base64-encoded", e);
        }
        if (keyBytes.length != AES_KEY_BYTES) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY must decode to exactly " + AES_KEY_BYTES
                            + " bytes (AES-256). Got " + keyBytes.length + ".");
        }
        aesKey = new SecretKeySpec(keyBytes, "AES");
        hmacKey = new SecretKeySpec(keyBytes, HMAC_ALG);
        log.info("EncryptionService initialised (AES-256-GCM + HMAC-SHA256)");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_ALG);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        if (ciphertextB64 == null) {
            return null;
        }
        try {
            byte[] in = Base64.getDecoder().decode(ciphertextB64);
            if (in.length < GCM_IV_BYTES + 1) {
                throw new IllegalStateException("Ciphertext too short to contain IV + payload");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ct = new byte[in.length - GCM_IV_BYTES];
            System.arraycopy(in, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(in, GCM_IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(AES_ALG);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed (wrong key or corrupted ciphertext)", e);
        }
    }

    /** Deterministic HMAC-SHA256, hex-encoded, suitable for a unique index. */
    public String hmac(String input) {
        if (input == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(hmacKey);
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }
}
