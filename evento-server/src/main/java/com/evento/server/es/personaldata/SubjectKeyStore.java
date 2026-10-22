package com.evento.server.es.personaldata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The keys of data subjects (3.0, crypto-shredding), in {@code es__subject_keys}: the bundles
 * encrypt the {@code @PersonalData} fields of payloads with them and ask for them over the bus.
 * Forgetting a subject deletes its row: every copy of its personal data, in events, snapshots and
 * backups of the event store, can no longer be read (a backup of this table taken before keeps the
 * key: keep this table's backups as short as the right to erasure requires).
 *
 * <p>Clusterable: every server reads and writes the same rows, and two servers creating the key of
 * one subject at once end up with the same key (the second insert does nothing, both read the
 * first).
 *
 * <p>With {@code evento.server.personal-data.master-key} (32 bytes, base64) the keys are stored
 * wrapped (AES-256-GCM, the subject as associated data), so the table alone does not open the
 * data; every server needs the same master key. Without it they are stored as they are, with a
 * warning the first time.
 */
@Component
public class SubjectKeyStore {

    private static final Logger log = LoggerFactory.getLogger(SubjectKeyStore.class);

    static final String TABLE = "es__subject_keys";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final byte[] masterKey;
    private final AtomicBoolean unwrappedWarned = new AtomicBoolean();

    public SubjectKeyStore(JdbcTemplate jdbc,
                           @Value("${evento.server.personal-data.master-key:}") String masterKey) {
        this.jdbc = jdbc;
        this.masterKey = parseMasterKey(masterKey);
    }

    static byte[] parseMasterKey(String value) {
        if (value == null || value.isBlank()) return null;
        byte[] key;
        try {
            key = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("evento.server.personal-data.master-key is not base64", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException("evento.server.personal-data.master-key must be " + KEY_BYTES
                    + " bytes (base64 of a 256-bit key), not " + key.length);
        }
        return key;
    }

    /**
     * The key of {@code subject}; with {@code create}, made when the subject has none.
     *
     * @return the key, or empty when the subject has none (never had, or was forgotten)
     */
    public Optional<byte[]> key(String subject, boolean create) {
        requireSubject(subject);
        try {
            if (create) {
                byte[] key = new byte[KEY_BYTES];
                RANDOM.nextBytes(key);
                boolean wrap = masterKey != null;
                int inserted = jdbc.update("INSERT INTO " + TABLE + " (subject, key_material, wrapped) VALUES (?, ?, ?) "
                        + "ON CONFLICT (subject) DO NOTHING", subject, wrap ? wrap(subject, key) : key, wrap);
                if (inserted == 1) {
                    if (!wrap && unwrappedWarned.compareAndSet(false, true)) {
                        log.warn("event=subject_keys_unwrapped reason=\"evento.server.personal-data.master-key is not "
                                + "set: data subject keys are stored as they are, so a copy of " + TABLE
                                + " opens the personal data\"");
                    }
                    log.info("event=subject_key_created subject={}", subject);
                    return Optional.of(key);
                }
            }
            var rows = jdbc.query("SELECT key_material, wrapped FROM " + TABLE + " WHERE subject = ?",
                    (rs, n) -> rs.getBoolean(2) ? unwrap(subject, rs.getBytes(1)) : rs.getBytes(1), subject);
            return rows.stream().findFirst();
        } catch (DataAccessException e) {
            throw missingTable(e);
        }
    }

    /**
     * Forgets {@code subject}: deletes its key.
     *
     * @return whether it had one
     */
    public boolean forget(String subject) {
        requireSubject(subject);
        try {
            boolean forgotten = jdbc.update("DELETE FROM " + TABLE + " WHERE subject = ?", subject) > 0;
            log.info("event=subject_forgotten subject={} hadKey={}", subject, forgotten);
            return forgotten;
        } catch (DataAccessException e) {
            throw missingTable(e);
        }
    }

    private static void requireSubject(String subject) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("a data subject is required");
        if (subject.length() > 1024) throw new IllegalArgumentException("a data subject is at most 1024 characters");
    }

    private RuntimeException missingTable(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "42P01".equals(sql.getSQLState())) {
                return new IllegalStateException(TABLE + " does not exist: let the server migrate its schema "
                        + "(spring.sql.init.mode=always) or apply db/upgrade/subject-keys.sql", e);
            }
        }
        return e;
    }

    private byte[] wrap(String subject, byte[] key) {
        try {
            var nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(subject.getBytes(StandardCharsets.UTF_8));
            var sealed = cipher.doFinal(key);
            var out = new byte[NONCE_BYTES + sealed.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(sealed, 0, out, NONCE_BYTES, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot wrap a data subject key", e);
        }
    }

    private byte[] unwrap(String subject, byte[] stored) {
        if (masterKey == null) {
            throw new IllegalStateException("the key of data subject " + subject + " is wrapped, and "
                    + "evento.server.personal-data.master-key is not set on this server");
        }
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(128, stored, 0, NONCE_BYTES));
            cipher.updateAAD(subject.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot unwrap the key of data subject " + subject
                    + ": evento.server.personal-data.master-key differs from the one it was wrapped with", e);
        }
    }
}
