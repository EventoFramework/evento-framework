package com.evento.common.messaging.outcome;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Durable record of the outcomes of keyed service commands, kept by the bundle that runs the
 * handlers (the inbox pattern). It closes the window the broker cannot close alone (G5): a
 * service handler that ran, and whose event was then not stored because the append failed or
 * the server died between the handler's answer and the append. The broker's retry forwards the
 * command again; with a store configured
 * ({@code EventoBundle.Builder#setServiceCommandOutcomeStore}) the bundle finds the outcome
 * recorded under the command's idempotency key and answers it again without running the
 * handler, and the broker stores that answer.
 *
 * <p>Every instance that may receive the retry must see the same store: in practice one table in
 * the database the service's handlers already write to. With {@link #recordInTransaction} the
 * outcome commits with the handler's own database side effects, which makes the handler
 * exactly-once for them: either both are committed, and a retry is answered from the store, or
 * neither is, and the retry runs the handler. Side effects outside that database (a payment
 * provider, a mail) are not covered by any store; pass the key downstream
 * ({@code ServiceCommandMessage#getIdempotencyKey()}) to a provider that deduplicates on it.
 *
 * <p>Only successes are recorded: a handler that throws records nothing, so a retry runs it
 * again, as the broker does.
 */
public interface ServiceCommandOutcomeStore extends AutoCloseable {

    /**
     * The outcome recorded for {@code idempotencyKey}, if any.
     *
     * @param idempotencyKey a non-null key
     */
    Optional<StoredOutcome> find(String idempotencyKey);

    /**
     * Records an outcome, unless one is already recorded for the key (the first one stays).
     *
     * @param commandType      the command's payload type (simple name), its identity: a key
     *                         presented later by another command type is refused, never answered
     *                         with this outcome
     * @param serializedResult the handler's result as {@link ServiceCommandOutcomes#serialize}
     *                         writes it, {@code null} for a handler that returned nothing
     * @return {@code true} when this call recorded it, {@code false} when the key already had one
     */
    boolean record(String idempotencyKey, String commandType, byte[] serializedResult);

    /**
     * Records an outcome in the caller's transaction on {@code connection}, so that it commits
     * (or rolls back) together with the handler's own writes on that connection. A store that
     * cannot join a JDBC transaction records nothing and answers {@code false}; the bundle then
     * records the outcome after the handler returns, as {@link #record} does.
     *
     * @return {@code true} when the outcome was written in the transaction
     * @throws DuplicateServiceOutcomeException when the key already has an outcome (another
     *                                          execution committed first): roll the transaction
     *                                          back and let the exception propagate, the bundle
     *                                          answers with the recorded outcome
     */
    default boolean recordInTransaction(Connection connection, String idempotencyKey, String commandType,
                                        byte[] serializedResult) throws SQLException {
        return false;
    }

    /**
     * Drops outcomes recorded strictly before {@code threshold}. A command retried after its
     * outcome is dropped runs again, so keep outcomes for longer than any retry can come (the
     * broker keeps its events, and their keys, forever).
     *
     * @return how many outcomes were removed
     */
    int sweepBefore(Instant threshold);

    @Override
    default void close() {
        // nothing to release by default
    }
}
