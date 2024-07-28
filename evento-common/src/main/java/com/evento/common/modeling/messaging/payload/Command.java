package com.evento.common.modeling.messaging.payload;


/**
 * The Command abstract class represents a command object that can be used in a software system.
 * It extends the Payload interface, which represents a payload object that can be used in a software system.
 */
public abstract class Command extends PayloadWithContext {


    /**
     * Retrieves the lock ID associated with the ServiceCommand.
     *
     * @return The lock ID associated with the ServiceCommand.
     */
    @SuppressWarnings("SameReturnValue")
    public abstract String getLockId();

}
