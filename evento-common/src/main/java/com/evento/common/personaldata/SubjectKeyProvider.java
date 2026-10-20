package com.evento.common.personaldata;

import java.util.Optional;

/**
 * Where the keys of data subjects come from. A bundle connected to Evento servers uses theirs
 * (kept in the event store's database, so every server and every bundle sees the same key); tests
 * use {@link InMemorySubjectKeyProvider}.
 */
public interface SubjectKeyProvider {

    /**
     * The AES-256 key of {@code subject}.
     *
     * @param create whether to create it when the subject has none (writing personal data);
     *               {@code false} when reading
     * @return the key, or empty when the subject has none: never had one, or was forgotten
     */
    Optional<byte[]> key(String subject, boolean create);

    /**
     * Deletes the key of {@code subject}: its personal data, wherever stored, can no longer be
     * read. A later write for the same subject starts a new key.
     *
     * @return whether there was a key to delete
     */
    boolean forget(String subject);
}
