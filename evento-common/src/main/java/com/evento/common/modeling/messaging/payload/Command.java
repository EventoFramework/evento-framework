package com.evento.common.modeling.messaging.payload;


/**
 * The Command class is an abstract class representing a command that can be sent to a service.
 * It extends the TrackablePayload class and defines additional methods to retrieve the ID of the
 * aggregate entity and the lock ID associated with the command.
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
