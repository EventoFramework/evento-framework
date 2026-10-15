package com.evento.common.versioning;

/**
 * A payload could not be upcast: its stored JSON is not a typed payload, or an {@link Upcaster}
 * failed on it. Names the payload and the revision the failing step started from, since the
 * fix is in that upcaster.
 */
public class UpcastException extends RuntimeException {

    private final String payloadName;
    private final int revision;

    public UpcastException(String payloadName, int revision, String message, Throwable cause) {
        super("cannot upcast " + payloadName + " revision " + revision + ": " + message, cause);
        this.payloadName = payloadName;
        this.revision = revision;
    }

    /** @return the simple name of the payload the failing step started from */
    public String getPayloadName() {
        return payloadName;
    }

    /** @return the revision the failing step started from */
    public int getRevision() {
        return revision;
    }
}
