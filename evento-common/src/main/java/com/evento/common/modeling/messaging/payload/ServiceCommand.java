package com.evento.common.modeling.messaging.payload;


/**
 * The ServiceCommand interface represents a command that can be sent to a service.
 * It extends the Command interface and defines an additional method to retrieve the lock ID associated with the command.
 */
public interface ServiceCommand extends Command {

	@SuppressWarnings("SameReturnValue")
    default String getLockId(){
		return null;
	}

	@Override
	default String getAggregateId() {
		return getLockId();
	}
}
