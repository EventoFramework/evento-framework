package org.evento.demo.pc.aggregate;

import org.evento.common.modeling.annotations.component.Aggregate;
import org.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import org.evento.common.modeling.annotations.handler.EventSourcingHandler;
import org.evento.demo.pc.api.PcCommand1;
import org.evento.demo.pc.api.PcCommand2;
import org.evento.demo.pc.api.PcEvent1;
import org.evento.demo.pc.api.PcEvent2;

@Aggregate
public class PcAggregate {

	@AggregateCommandHandler(init = true)
	public PcEvent1 handle(PcCommand1 command1, PcAggregateState state) {
		var evt = new PcEvent1();
		evt.setPcId(command1.getPcId());
		return evt;
	}

	@EventSourcingHandler
	public PcAggregateState handle(PcEvent1 event1, PcAggregateState state) {
		return state;
	}

	@AggregateCommandHandler
	public PcEvent2 handle(PcCommand2 command2, PcAggregateState state) {
		var evt = new PcEvent2();
		evt.setPcId(command2.getPcId());
		return evt;
	}

	@EventSourcingHandler
	public PcAggregateState handle(PcEvent2 event2, PcAggregateState state) {
		return state;
	}
}
