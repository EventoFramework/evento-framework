package org.eventrails.modeling.messaging.payload;

public abstract class ServiceCommand extends Command {
	public abstract String getLockId();
}
