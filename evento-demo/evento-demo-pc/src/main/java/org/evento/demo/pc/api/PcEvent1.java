package org.evento.demo.pc.api;

import org.evento.common.modeling.messaging.payload.DomainEvent;

public class PcEvent1 extends DomainEvent {

	private String pcId;

	public String getPcId() {
		return pcId;
	}

	public void setPcId(String pcId) {
		this.pcId = pcId;
	}
}
