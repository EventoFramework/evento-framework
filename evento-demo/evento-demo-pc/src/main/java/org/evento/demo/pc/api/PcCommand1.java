package org.evento.demo.pc.api;

import lombok.Getter;
import lombok.Setter;
import org.evento.common.modeling.messaging.payload.DomainCommand;

@Setter
@Getter
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

}
