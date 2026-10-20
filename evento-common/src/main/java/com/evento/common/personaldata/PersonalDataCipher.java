package com.evento.common.personaldata;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * How a {@link PersonalData} value is stored: AES-256-GCM under the subject's key, with the
 * subject as associated data, as the JSON string
 * {@code "evento-pd:1:<subject, base64url>:<nonce + ciphertext, base64>"}. The subject travels with
 * the value so it can be read without its object's other fields.
 */
public final class PersonalDataCipher {

    /** Length of a subject key. */
    public static final int KEY_BYTES = 32;

    static final String PREFIX = "evento-pd:1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PersonalDataCipher() {
    }

    /** Whether {@code stored} is an encrypted value. */
    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** The subject an encrypted value belongs to. */
    public static String subjectOf(String stored) {
        int end = stored.indexOf(':', PREFIX.length());
        if (end < 0) throw new IllegalArgumentException("not an encrypted personal data value");
        return new String(Base64.getUrlDecoder().decode(stored.substring(PREFIX.length(), end)), StandardCharsets.UTF_8);
    }

    /** Encrypts {@code plaintext} (the value's JSON) for {@code subject}. */
    public static String encrypt(String subject, byte[] key, String plaintext) {
        try {
            var nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(subject.getBytes(StandardCharsets.UTF_8));
            var sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var out = new byte[NONCE_BYTES + sealed.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(sealed, 0, out, NONCE_BYTES, sealed.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(subject.getBytes(StandardCharsets.UTF_8))
                    + ":" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot encrypt personal data", e);
        }
    }

    /**
     * The plaintext of an encrypted value, or empty when {@code key} does not open it: the subject
     * was forgotten and has a new key since.
     */
    public static Optional<String> decrypt(String stored, byte[] key) {
        int end = stored.indexOf(':', PREFIX.length());
        var subject = Base64.getUrlDecoder().decode(stored.substring(PREFIX.length(), end));
        var in = Base64.getDecoder().decode(stored.substring(end + 1));
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, in, 0, NONCE_BYTES));
            cipher.updateAAD(subject);
            return Optional.of(new String(cipher.doFinal(in, NONCE_BYTES, in.length - NONCE_BYTES), StandardCharsets.UTF_8));
        } catch (AEADBadTagException e) {
            return Optional.empty();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot decrypt personal data", e);
        }
    }
}
