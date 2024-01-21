package org.evento.demo.pc.api;

import lombok.Getter;
import lombok.Setter;
import org.evento.common.modeling.messaging.payload.DomainEvent;

@Setter
@Getter
public class PcEvent1 extends DomainEvent {

	private String pcId;

}
