package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Phase 4 fix 4.15: migrate the {@code users} table for PII column-level encryption.
 *
 * <p>Steps (idempotent, wrapped in the Flyway transaction):
 * <ol>
 *   <li>Add {@code national_id_hash VARCHAR(64)} as a nullable column.</li>
 *   <li>Widen {@code national_id} to {@code VARCHAR(500)} so it can hold base64(IV ‖ ciphertext).</li>
 *   <li>For each existing row: HMAC the plaintext into {@code national_id_hash},
 *       then encrypt the plaintext in place into {@code national_id}.</li>
 *   <li>Drop the legacy unique index on the plaintext column.</li>
 *   <li>Mark {@code national_id_hash} NOT NULL and add a unique index.</li>
 * </ol>
 *
 * <p>Flyway Java migrations run before Spring is fully wired, so they cannot autowire
 * {@code EncryptionService}. The key is resolved from the Flyway placeholder
 * {@code app_encryption_key} (which Spring populates from {@code app.encryption.key} in
 * application.properties) and falls back to the {@code APP_ENCRYPTION_KEY} env var. The
 * crypto here MUST stay in sync with that service (AES-256-GCM with 12-byte IV + 128-bit
 * tag; HMAC-SHA256 over the plaintext).
 */
public class V6__Encrypt_national_id extends BaseJavaMigration {

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    @Override
    public void migrate(Context context) throws Exception {
        byte[] keyBytes = loadKey(context);
        SecretKeySpec aesKey = new SecretKeySpec(keyBytes, "AES");
        SecretKeySpec hmacKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        try (Statement st = context.getConnection().createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS national_id_hash VARCHAR(64)");
            st.execute("ALTER TABLE users ALTER COLUMN national_id TYPE VARCHAR(500)");
        }

        // Read plaintext rows; write back ciphertext + hash. Done in a separate
        // statement scope from the schema change so the new column is visible.
        List<long[]> ids = new ArrayList<>();
        try (Statement st = context.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, national_id FROM users WHERE national_id_hash IS NULL")) {
            while (rs.next()) {
                ids.add(new long[]{rs.getLong("id"), 0});
            }
        }
        // Re-iterate fetching plaintext explicitly to avoid keeping a long-lived ResultSet open.
        try (PreparedStatement update = context.getConnection().prepareStatement(
                "UPDATE users SET national_id = ?, national_id_hash = ? WHERE id = ?");
             Statement reader = context.getConnection().createStatement();
             ResultSet rs = reader.executeQuery(
                     "SELECT id, national_id FROM users WHERE national_id_hash IS NULL")) {
            int n = 0;
            while (rs.next()) {
                long id = rs.getLong("id");
                String plaintext = rs.getString("national_id");
                if (plaintext == null) {
                    continue;
                }
                String ciphertext = encrypt(plaintext, aesKey);
                String hash = hmacHex(plaintext, hmacKey);
                update.setString(1, ciphertext);
                update.setString(2, hash);
                update.setLong(3, id);
                update.addBatch();
                n++;
            }
            if (n > 0) {
                update.executeBatch();
            }
        }

        try (Statement st = context.getConnection().createStatement()) {
            // The original unique constraint name varies across Hibernate/Flyway dialects;
            // PostgreSQL auto-generates "users_national_id_key" when the column is declared
            // UNIQUE inline. Drop conditionally.
            st.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_national_id_key");
            st.execute("ALTER TABLE users ALTER COLUMN national_id_hash SET NOT NULL");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_national_id_hash ON users(national_id_hash)");
        }
    }

    /**
     * Same key requirement as {@code EncryptionService}, resolved without a Spring context.
     * Order: the Spring-populated Flyway placeholder {@code app_encryption_key} first (so
     * local dev can keep the key in application.properties), then the {@code APP_ENCRYPTION_KEY}
     * environment variable (prod).
     */
    private static byte[] loadKey(Context context) {
        String b64 = context.getConfiguration().getPlaceholders().get("app_encryption_key");
        if (b64 == null || b64.isBlank()) {
            b64 = System.getenv("APP_ENCRYPTION_KEY");
        }
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException(
                    "Encryption key required for V6__Encrypt_national_id: set "
                            + "spring.flyway.placeholders.app_encryption_key (or app.encryption.key) "
                            + "or the APP_ENCRYPTION_KEY env var");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be base64-encoded", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256). Got " + bytes.length);
        }
        return bytes;
    }

    private static String encrypt(String plaintext, SecretKeySpec key) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    private static String hmacHex(String input, SecretKeySpec key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
