package com.evento.lab.cluster.chaos;

/**
 * The lab could not be driven (docker missing, stack down, API unreachable).
 * Unlike a violated invariant, which is a result, this is the only thing a
 * scenario hard-fails on.
 */
public class HarnessException extends RuntimeException {

    public HarnessException(String message) {
        super(message);
    }

    public HarnessException(String message, Throwable cause) {
        super(message, cause);
    }
}
