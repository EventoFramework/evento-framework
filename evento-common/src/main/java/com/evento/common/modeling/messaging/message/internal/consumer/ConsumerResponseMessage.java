package com.evento.common.modeling.messaging.message.internal.consumer;


import java.io.Serializable;

/**
 * The ConsumerSetEventRetryResponseMessage class represents a response message for setting the retry flag
 * of a consumer event.
 *
 * This class is Serializable, allowing instances of it to be serialized and deserialized.
 *
 * The response message contains a boolean field 'success' which indicates whether the retry flag was
 * successfully set or not.
 */
public class ConsumerResponseMessage implements Serializable {
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
