package com.evento.common.modeling.messaging.payload;


/**
 * The ServiceCommand abstract class represents a command that can be sent to a service.
 * It extends the Command interface and defines an additional method to retrieve the lock ID associated with the command.
 */
public abstract class ServiceCommand extends Command {

	@SuppressWarnings("SameReturnValue")
	@Override
    public String getLockId(){
		return null;
	}

	@Override
	public String getAggregateId() {
		return getLockId();
	}
}
