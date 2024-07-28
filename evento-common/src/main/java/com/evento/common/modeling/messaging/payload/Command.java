package com.evento.common.modeling.messaging.payload;


/**
 * The Command abstract class represents a command object that can be used in a software system.
 * It extends the Payload interface, which represents a payload object that can be used in a software system.
 */
public abstract class Command extends TrackablePayload {

    /**
     * Retrieves the ID of the aggregate entity.
     * The aggregate entity represents a group of related objects that are treated as a single unit.
     * This method should be implemented by subclasses to provide the aggregate ID.
     *
     * @return The ID of the aggregate entity.
     */
    public abstract String getAggregateId();

    /**
     * Retrieves the lock ID associated with the ServiceCommand.
     *
     * @return The lock ID associated with the ServiceCommand.
     */
    @SuppressWarnings("SameReturnValue")
    public abstract String getLockId();

}
