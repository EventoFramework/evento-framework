package com.evento.application.performance;

import lombok.Getter;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Payload;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Extension hook for correlating and tracking messages for performance monitoring and tracing.
 *
 * <p><b>The base implementation is a deliberate no-op:</b> {@link #doTrack} simply runs the
 * transaction, {@link #traceSend} simply sends, and {@link #correlate(Metadata, Message)} returns
 * the metadata unchanged, so a bundle without tracing pays nothing.
 *
 * <p>Three hooks, all overridable: {@link #doTrack} around every handler, {@link #traceSend}
 * around every command or query a handler or the application sends (a stream query is only
 * {@linkplain #correlate correlated}), {@link #correlate} on the metadata of what is sent. The
 * OpenTelemetry implementation is {@code com.evento.opentelemetry.OpenTelemetryTracingAgent}
 * ({@code evento-opentelemetry}).
 */
@Getter
public class TracingAgent {

    // Fields for bundle identification
    private final String bundleId;
    private final long bundleVersion;

    /**
     * Constructs a new TracingAgent with the specified bundle identifier and version.
     *
     * @param bundleId     The identifier of the bundle.
     * @param bundleVersion The version of the bundle.
     */
    public TracingAgent(String bundleId, long bundleVersion) {
        this.bundleId = bundleId;
        this.bundleVersion = bundleVersion;
    }

    /**
     * Correlates metadata from one message to another message.
     *
     * @param from The source message.
     * @param to   The destination message.
     */
    public void correlate(Message<?> from, Message<?> to) {
        to.setMetadata(correlate(to.getMetadata(), from));
    }

    /**
     * Tracks the execution of a transaction with the provided parameters.
     *
     * @param message             The message being tracked.
     * @param component           The component associated with the tracking.
     * @param trackingAnnotation  The tracking annotation (unused in this example).
     * @param transaction         The transaction to be tracked.
     * @param <T>                 The type of the result returned by the transaction.
     * @return                    The result of the transaction.
     * @throws Throwable          If an exception occurs during the transaction.
     */
    public final <T> T track(Message<?> message,
                       String component,
                       Track trackingAnnotation,
                       Transaction<T> transaction)
            throws Throwable {
        return doTrack(message, component, trackingAnnotation, transaction);
    }

    /**
     * Tracks the execution of a transaction with the specified parameters.
     *
     * @param message            The message being tracked. Contains metadata and payload information.
     * @param component          The component associated with the tracking operation.
     * @param trackingAnnotation The tracking annotation providing context for the tracking (unused in the logic).
     * @param transaction        The transaction to be executed and tracked.
     * @param <T>                The type of the result returned by the transaction.
     * @return The result of the executed transaction.
     * @throws Throwable         If an exception occurs during the execution of the transaction.
     */
    protected <T> T doTrack(Message<?> message,
                            String component,
                            Track trackingAnnotation,
                            Transaction<T> transaction) throws Throwable {
        return transaction.run();
    }

    /**
     * Sends one command or query: {@code send} puts the given metadata on the wire and returns the
     * reply. The base implementation sends {@link #correlate(Metadata, Message) correlated}
     * metadata; a tracing implementation opens a span that lasts until the reply and puts that
     * span's context in the metadata instead.
     *
     * @param payload        the command or query sent
     * @param metadata       the caller's metadata, possibly {@code null}; not modified
     * @param handledMessage the message being handled while sending, or {@code null}
     * @param send           sends with the metadata it is given
     * @param <R>            the reply type
     * @return the reply of {@code send}
     */
    public <R> CompletableFuture<R> traceSend(Payload payload, Metadata metadata, Message<?> handledMessage,
                                              Function<Metadata, CompletableFuture<R>> send) {
        return send.apply(correlate(metadata, handledMessage));
    }

    /**
     * Correlates metadata using the provided handled message.
     *
     * @param metadata           The metadata to be correlated.
     * @param handledMessage     The handled message used for correlation.
     * @return                   The correlated metadata.
     */
    public Metadata correlate(Metadata metadata, Message<?> handledMessage) {
        // Currently, this method does not perform any correlation
        return metadata;
    }

    /**
     * Interface for defining a transaction that can be tracked.
     *
     * @param <T> The type of result returned by the transaction.
     */
    public interface Transaction<T> {
        /**
         * Runs the transaction.
         *
         * @return The result of the transaction.
         * @throws Throwable If an exception occurs during the transaction.
         */
        T run() throws Throwable;
    }


}
