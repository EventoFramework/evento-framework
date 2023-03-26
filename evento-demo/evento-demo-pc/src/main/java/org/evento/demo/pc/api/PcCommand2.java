package org.evento.demo.pc.api;

import org.evento.common.modeling.messaging.payload.DomainCommand;

public class PcCommand2 extends DomainCommand {

	private String pcId;

	public PcCommand2() {
	}

	public PcCommand2(String pcId) {
		this.pcId = pcId;
	}

	@Override
	public String getAggregateId() {
		return pcId;
	}

	public String getPcId() {
		return pcId;
	}

	public void setPcId(String pcId) {
		this.pcId = pcId;
	}
}
