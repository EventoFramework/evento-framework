package com.evento.application.performance;

import com.evento.common.performance.AutoscalingProtocol;
import lombok.Getter;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import lombok.Setter;

/**
 * The TracingAgent class provides functionality for correlating and tracking messages
 * within the application for performance monitoring and tracing purposes.
 */
@Getter
public class TracingAgent {

    // Fields for bundle identification
    private final String bundleId;
    private final long bundleVersion;
    @Setter
    private AutoscalingProtocol autoscalingProtocol;

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
     * @throws Exception          If an exception occurs during the transaction.
     */
    public final <T> T track(Message<?> message,
                       String component,
                       Track trackingAnnotation,
                       Transaction<T> transaction)
            throws Exception {
        try {
            if(autoscalingProtocol != null){
                autoscalingProtocol.arrival();
            }
            return doTrack(message, component, trackingAnnotation, transaction);
        }finally {
            if(autoscalingProtocol != null){
                autoscalingProtocol.departure();
            }
        }
    }

    /**
     * Tracks the internal implementation
     *
     * @param message             The message being tracked.
     * @param component           The component associated with the tracking.
     * @param trackingAnnotation  The tracking annotation (unused in this example).
     * @param transaction         The transaction to be tracked.
     * @param <T>                 The type of the result returned by the transaction.
     * @return                    The result of the transaction.
     * @throws Exception          If an exception occurs during the transaction.
     */
    protected <T> T doTrack(Message<?> message,
                            String component,
                            Track trackingAnnotation,
                            Transaction<T> transaction) throws Exception {
        return transaction.run();
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
