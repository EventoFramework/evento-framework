package org.evento.common.modeling.messaging.payload;

public abstract class ServiceCommand extends Command {
	public abstract String getLockId();
}
