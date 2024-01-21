package org.evento.demo.command.aggregate;

import lombok.Getter;
import lombok.Setter;
import org.evento.common.modeling.state.AggregateState;

@Setter
@Getter
public class DemoAggregateState extends AggregateState {
	private long value;


	public DemoAggregateState(long value) {
		this.value = value;
	}

	public DemoAggregateState() {
	}

}
