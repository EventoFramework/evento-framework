package com.evento.common.messaging.outcome;

import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.serialization.ObjectMapperUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The outcome of the keyed service command being handled on this thread, and the way for its
 * handler to record that outcome in its own transaction.
 *
 * <p>Injectable into a service {@code @CommandHandler} (declare a parameter of this type), and
 * reachable from a {@code MessageHandlerInterceptor} through {@link #current()}. When the bundle
 * has a {@link ServiceCommandOutcomeStore} and the command carries an idempotency key, the bundle
 * records the handler's result after it returns. That alone closes the broker's window (a
 * handler that ran while its event was not stored is not run again), but leaves a smaller one:
 * the process dying between the handler's side effects and that record. A handler whose side
 * effects are writes to a database closes it by recording in the same transaction:
 *
 * <pre>{@code
 * @CommandHandler
 * PaymentDoneEvent handle(PayCommand cmd, ServiceCommandOutcomes outcomes) throws Exception {
 *     try (var con = dataSource.getConnection()) {
 *         con.setAutoCommit(false);
 *         try {
 *             insertPayment(con, cmd);
 *             var event = new PaymentDoneEvent(cmd.getOrderId());
 *             outcomes.recordWith(con, event);   // no-op without a key or a JDBC store
 *             con.commit();
 *             return event;
 *         } catch (Exception e) {
 *             con.rollback();                    // a DuplicateServiceOutcomeException too
 *             throw e;
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Then the payment row and the outcome commit together or not at all, and a retry of the key
 * is answered with the recorded event. Two executions racing on one key (a broker that lost its
 * claim) collide on the outcome's primary key: the second gets
 * {@link DuplicateServiceOutcomeException}, rolls back, and is answered with the first one's
 * outcome. Side effects outside the database (a payment provider) need the key passed
 * downstream: {@link #idempotencyKey()}.
 *
 * <p>Outside a keyed invocation (no key, no store, not in a handler) every method is a no-op:
 * {@link #isActive()} is {@code false}, {@link #recordWith} records nothing.
 */
public final class ServiceCommandOutcomes {

    private static final ThreadLocal<ServiceCommandOutcomes> CURRENT = new ThreadLocal<>();
    private static final ServiceCommandOutcomes NONE = new ServiceCommandOutcomes(null, null, null);

    private final String idempotencyKey;
    private final String commandType;
    private final ServiceCommandOutcomeStore store;
    private volatile boolean recorded;

    /**
     * A handle for one invocation. Created by the bundle; applications receive it injected or
     * through {@link #current()}.
     */
    public ServiceCommandOutcomes(String idempotencyKey, String commandType, ServiceCommandOutcomeStore store) {
        this.idempotencyKey = idempotencyKey;
        this.commandType = commandType;
        this.store = store;
    }

    /** The handle of the service command handled on this thread; an inactive one outside. */
    public static ServiceCommandOutcomes current() {
        var c = CURRENT.get();
        return c == null ? NONE : c;
    }

    /** The inactive handle. */
    public static ServiceCommandOutcomes none() {
        return NONE;
    }

    /**
     * Makes {@code outcomes} the {@link #current()} one on this thread until the returned scope is
     * closed. Used by the bundle around a handler invocation.
     */
    public static Scope bind(ServiceCommandOutcomes outcomes) {
        var previous = CURRENT.get();
        CURRENT.set(outcomes);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    /** A binding of {@link #bind}; closing it restores the previous one. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /** The command's idempotency key, {@code null} without one. Pass it to downstream systems. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** The command's payload type (simple name), {@code null} outside a keyed invocation. */
    public String commandType() {
        return commandType;
    }

    /** Whether the outcome of this invocation will be recorded (a key and a store). */
    public boolean isActive() {
        return idempotencyKey != null && store != null;
    }

    /**
     * Records {@code result} as this command's outcome in the transaction of {@code connection}
     * (which must not be in autocommit mode for the binding to mean anything). Return the same
     * result from the handler. Does nothing and answers {@code false} when the invocation is not
     * active or the store cannot join a JDBC transaction (the bundle then records after the
     * handler returns).
     *
     * @param result the handler's result, {@code null} for a handler that returns nothing
     * @return {@code true} when the outcome was written in the transaction
     * @throws DuplicateServiceOutcomeException when another execution of the key recorded first:
     *                                          roll back and rethrow
     */
    public boolean recordWith(Connection connection, ServiceEvent result) throws SQLException {
        if (!isActive()) return false;
        if (store.recordInTransaction(connection, idempotencyKey, commandType, serialize(result))) {
            recorded = true;
            return true;
        }
        return false;
    }

    /** Whether the handler recorded the outcome itself ({@link #recordWith}). */
    public boolean isRecorded() {
        return recorded;
    }

    /** The stored form of a handler result: the payload JSON with its type, {@code null} for none. */
    public static byte[] serialize(ServiceEvent result) {
        if (result == null) return null;
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(result).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize the result of a service command: " + result.getClass(), e);
        }
    }

    /** The result {@link #serialize} wrote, {@code null} for none. */
    public static ServiceEvent deserialize(byte[] serialized) {
        if (serialized == null) return null;
        try {
            return (ServiceEvent) ObjectMapperUtils.getPayloadObjectMapper()
                    .readValue(new String(serialized, StandardCharsets.UTF_8), Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot read a recorded service command outcome", e);
        }
    }
}
