package com.evento.common.modeling.messaging.message.internal.consumer;

import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.StoredSagaState;

import java.io.Serializable;
import java.util.Collection;


/**
 * The ConsumerFetchStatusResponseMessage class represents a response message containing the fetch status for a consumer in an event sourcing system.
 * It provides information about the last event sequence number processed by the consumer, the dead events that failed to be processed, and the stored saga state.
 *
 * This class implements the Serializable interface to enable the object to be written to an output stream and deserialized back into a Java object.
 *
 * The class has the following attributes:
 * - lastEventSequenceNumber: A long value representing the sequence number of the last event processed by the consumer.
 * - deadEvents: An ArrayList of DeadPublishedEvent objects representing the events that failed to be processed by the consumer.
 * - sagaState: A StoredSagaState object representing the stored state of a saga.
 *
 * The class provides the following methods:
 * - getLastEventSequenceNumber: Returns the last event sequence number processed by the consumer.
 * - setLastEventSequenceNumber: Sets the last event sequence number processed by the consumer.
 * - getDeadEvents: Returns the list of dead events.
 * - setDeadEvents: Sets the list of dead events.
 * - getSagaState: Returns the stored saga state.
 * - setSagaState: Sets the stored saga state.
 */
public class ConsumerProcessDeadQueueResponseMessage implements Serializable {
    private boolean success;

    /**
     * Returns a boolean value indicating whether the retry flag was successfully set or not.
     *
     * @return true if the retry flag was successfully set, false otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets the success flag for the response message.
     *
     * @param success a boolean value indicating whether the retry flag was successfully set or not.
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }
}
