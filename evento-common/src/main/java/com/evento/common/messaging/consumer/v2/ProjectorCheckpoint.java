package com.evento.common.messaging.consumer.v2;

/** Checkpoint for a projector consumer (idempotent state update per event). */
public record ProjectorCheckpoint(long lastSequenceNumber) implements ConsumerCheckpoint {
}
