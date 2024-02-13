package com.evento.common.modeling.messaging.payload;


/**
 * The Command interface represents a command object that can be used in a software system.
 * It extends the Payload interface, which represents a payload object that can be used in a software system.
 */
public interface Command extends Payload {
    /**
     * Retrieves the ID of the aggregate that the command is targeting.
     *
     * @return The aggregate ID as a string.
     */
    String getAggregateId();

    /**
     * Retrieves the lock ID associated with the ServiceCommand.
     *
     * @return The lock ID associated with the ServiceCommand.
     */
    @SuppressWarnings("SameReturnValue")
    String getLockId();

}
