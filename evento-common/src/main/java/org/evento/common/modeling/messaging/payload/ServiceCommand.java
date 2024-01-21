package org.evento.common.modeling.messaging.payload;

/**
 * The ServiceCommand class represents an abstract command that can be sent and executed in a service context.
 * It extends the Command class.
 */
public abstract class ServiceCommand extends Command {
	/**
	 * Retrieves the lock ID associated with the ServiceCommand.
	 *
	 * @return The lock ID associated with the ServiceCommand.
     */
	public String getLockId(){
		return null;
	}
}
