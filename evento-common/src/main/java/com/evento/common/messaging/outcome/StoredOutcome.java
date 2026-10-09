package com.evento.common.messaging.outcome;

import java.time.Instant;

/**
 * An outcome recorded by a {@link ServiceCommandOutcomeStore}.
 *
 * @param idempotencyKey   the command's key
 * @param commandType      the command's payload type (simple name) that owns the key
 * @param serializedResult the handler's result ({@link ServiceCommandOutcomes#serialize}),
 *                         {@code null} when it returned nothing
 * @param recordedAt       when it was recorded (store clock)
 */
public record StoredOutcome(String idempotencyKey, String commandType, byte[] serializedResult, Instant recordedAt) {

    /** Whether the handler returned nothing. */
    public boolean isVoid() {
        return serializedResult == null;
    }
}
