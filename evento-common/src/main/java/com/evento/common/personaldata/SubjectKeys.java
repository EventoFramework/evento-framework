package com.evento.common.personaldata;

import java.util.Optional;

/**
 * The {@link SubjectKeyProvider} the payload serializer uses: one per JVM, like the payload
 * mapper itself. An {@code EventoBundle} installs the one of its servers when it starts.
 */
public final class SubjectKeys {

    private static volatile SubjectKeyProvider provider;

    private SubjectKeys() {
    }

    /** Uses {@code provider} from now on. */
    public static void install(SubjectKeyProvider provider) {
        SubjectKeys.provider = provider;
    }

    /** Stops using {@code provider}, if it is still the one installed. */
    public static synchronized void uninstall(SubjectKeyProvider provider) {
        if (SubjectKeys.provider == provider) SubjectKeys.provider = null;
    }

    /** Installs {@code provider} unless one is installed already; returns the one in use. */
    public static synchronized SubjectKeyProvider installIfAbsent(SubjectKeyProvider provider) {
        if (SubjectKeys.provider == null) SubjectKeys.provider = provider;
        return SubjectKeys.provider;
    }

    /** The provider in use, if any. */
    public static Optional<SubjectKeyProvider> current() {
        return Optional.ofNullable(provider);
    }

    /**
     * Forgets {@code subject} (the right to erasure): deletes its key, so its personal data can no
     * longer be read anywhere. Read models that copied it in clear are the application's to clean.
     *
     * @return whether the subject had a key
     */
    public static boolean forget(String subject) {
        return required().forget(subject);
    }

    static SubjectKeyProvider required() {
        var p = provider;
        if (p == null) {
            throw new IllegalStateException("No SubjectKeyProvider installed: @PersonalData needs an EventoBundle "
                    + "connected to Evento servers, or SubjectKeys.install(...) (InMemorySubjectKeyProvider in tests)");
        }
        return p;
    }
}
