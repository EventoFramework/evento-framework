package com.evento.common.messaging.consumer.v2;

/** Checkpoint for an observer consumer (at-least-once side effects, paired with {@link DedupeStore}). */
public record EventCheckpoint(long lastSequenceNumber) implements ConsumerCheckpoint {
}
