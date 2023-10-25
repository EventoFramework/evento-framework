package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.ServiceCommand;

public class ServiceCommandMessage extends CommandMessage<ServiceCommand> {

	private String lockId;
	public ServiceCommandMessage(ServiceCommand command) {
		super(command);
		lockId = command.getLockId();
	}

	public ServiceCommandMessage() {
	}

	public void setLockId(String lockId) {
		this.lockId = lockId;
	}

	public String getLockId() {
		return lockId;
	}
}
