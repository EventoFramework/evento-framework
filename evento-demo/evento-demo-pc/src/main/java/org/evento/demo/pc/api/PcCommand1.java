package org.evento.demo.pc.api;

import org.evento.common.modeling.messaging.payload.DomainCommand;

public class PcCommand1 extends DomainCommand {

	private String pcId;

	public PcCommand1(String pcId) {
		this.pcId = pcId;
	}

	public PcCommand1() {
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
