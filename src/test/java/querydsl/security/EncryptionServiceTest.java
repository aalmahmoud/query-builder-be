package querydsl.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Phase 4 fix 4.15 — AES-GCM encryption + HMAC hash.
 */
@SpringBootTest
@ActiveProfiles("test")
class EncryptionServiceTest {

    @Autowired private EncryptionService encryption;

    @Test
    void encrypt_thenDecrypt_roundTrips() {
        String plaintext = "1234567890";
        String ciphertext = encryption.encrypt(plaintext);
        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, encryption.decrypt(ciphertext));
    }

    @Test
    void encrypt_isNonDeterministic() {
        // AES-GCM uses a random IV per call. Same plaintext, different ciphertext.
        String plaintext = "abc";
        String c1 = encryption.encrypt(plaintext);
        String c2 = encryption.encrypt(plaintext);
        assertNotEquals(c1, c2);
        // Both decrypt back to the same plaintext.
        assertEquals(plaintext, encryption.decrypt(c1));
        assertEquals(plaintext, encryption.decrypt(c2));
    }

    @Test
    void hmac_isDeterministic() {
        String input = "1000000001";
        assertEquals(encryption.hmac(input), encryption.hmac(input));
    }

    @Test
    void hmac_differsByInput() {
        assertNotEquals(encryption.hmac("a"), encryption.hmac("b"));
    }

    @Test
    void hmac_isHexAndFixedLength() {
        String digest = encryption.hmac("anything");
        assertNotNull(digest);
        assertEquals(64, digest.length(), "HMAC-SHA256 hex should be 64 chars");
        assertTrue(digest.matches("[0-9a-f]+"), "HMAC should be lowercase hex");
    }

    @Test
    void encryptNull_returnsNull() {
        assertNull(encryption.encrypt(null));
    }

    @Test
    void decryptNull_returnsNull() {
        assertNull(encryption.decrypt(null));
    }

    @Test
    void hmacNull_returnsNull() {
        assertNull(encryption.hmac(null));
    }

    @Test
    void decrypt_corruptedCiphertext_throws() {
        assertThrows(IllegalStateException.class,
                () -> encryption.decrypt("not-real-ciphertext-base64==="));
    }
}
