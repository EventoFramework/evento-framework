/**
 * v2 consumer state SPI — replaces the monolithic
 * {@link com.evento.common.messaging.consumer.ConsumerStateStore} abstract class.
 *
 * <p>The {@link com.evento.common.messaging.consumer.v2.ConsumerProcessor}
 * owns the v1-shape consume loops (projector / observer / saga + their
 * dead-letter variants, {@code handleLastError}, {@code toConsumerStatus}).
 * Persistence is split across five focused SPIs:
 *
 * <ul>
 *   <li>{@link com.evento.common.messaging.consumer.v2.ConsumerLock} — cross-instance
 *       exclusive zone per {@code consumerId}.</li>
 *   <li>{@link com.evento.common.messaging.consumer.v2.ConsumerStateStore} —
 *       checkpoint (with optimistic versioning) + enabled flag + error history.</li>
 *   <li>{@link com.evento.common.messaging.consumer.v2.SagaStateStore} — saga
 *       instance lookup by association.</li>
 *   <li>{@link com.evento.common.messaging.consumer.v2.DeadEventQueue} — per-consumer
 *       dead-letter queue.</li>
 *   <li>{@link com.evento.common.messaging.consumer.v2.DedupeStore} — optional
 *       event-id dedupe for observer exactly-once.</li>
 * </ul>
 *
 * <p>Default in-memory implementations under
 * {@link com.evento.common.messaging.consumer.v2.impl} cover tests and
 * single-JVM deployments. Production-grade JDBC impls live in
 * {@code evento-consumer-state-store-jdbc-v2}.
 */
package com.evento.common.messaging.consumer.v2;
