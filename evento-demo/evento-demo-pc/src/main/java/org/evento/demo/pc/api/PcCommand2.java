package org.evento.demo.pc.api;

import lombok.Getter;
import lombok.Setter;
import org.evento.common.modeling.messaging.payload.DomainCommand;

@Setter
@Getter
public class PcCommand2 implements DomainCommand {

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

}
