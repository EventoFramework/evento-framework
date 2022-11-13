package org.eventrails.common.modeling.messaging.payload;

public abstract class ServiceCommand extends Command {
	public abstract String getLockId();
}
