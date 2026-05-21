package com.evento.common.messaging.consumer.v2;

/**
 * Checkpoint for a saga consumer. Saga <em>instances</em> live in a separate
 * store keyed by association; this record is only the head position the engine
 * is processing.
 */
public record SagaCheckpoint(long lastSequenceNumber) implements ConsumerCheckpoint {
}
