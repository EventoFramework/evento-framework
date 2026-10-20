package com.evento.common.personaldata;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keys in memory, lost with the JVM: for tests (the {@code evento-test} fixtures install one when
 * none is), never for data that must be read again after a restart.
 */
public final class InMemorySubjectKeyProvider implements SubjectKeyProvider {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, byte[]> keys = new ConcurrentHashMap<>();

    @Override
    public Optional<byte[]> key(String subject, boolean create) {
        if (!create) return Optional.ofNullable(keys.get(subject));
        return Optional.of(keys.computeIfAbsent(subject, s -> {
            var key = new byte[PersonalDataCipher.KEY_BYTES];
            RANDOM.nextBytes(key);
            return key;
        }));
    }

    @Override
    public boolean forget(String subject) {
        return keys.remove(subject) != null;
    }
}
