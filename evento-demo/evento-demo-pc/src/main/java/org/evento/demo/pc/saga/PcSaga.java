package org.evento.demo.pc.saga;

import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.modeling.annotations.component.Saga;
import org.evento.common.modeling.annotations.handler.SagaEventHandler;
import org.evento.demo.pc.api.PcCommand2;
import org.evento.demo.pc.api.PcEvent1;
import org.evento.demo.pc.api.PcEvent2;

@Saga(version = 1)
public class PcSaga {

	@SagaEventHandler(init = true, associationProperty = "pcId")
	public PcSagaState on(PcEvent1 event1, PcSagaState state, CommandGateway commandGateway) {
		var s = new PcSagaState();
		s.setAssociation("pcId", event1.getPcId());
		commandGateway.sendAndWait(new PcCommand2(event1.getPcId()));
		return s;
	}

	@SagaEventHandler(init = true, associationProperty = "pcId")
	public PcSagaState on(PcEvent2 event2, PcSagaState state) {
		state.setEnded(true);
		return state;
	}
}
