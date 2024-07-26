package com.evento.demo.command.aggregate;

import lombok.Getter;
import lombok.Setter;
import com.evento.common.modeling.state.AggregateState;

@Setter
@Getter
public class DemoAggregateState extends AggregateState {
	private long value;
	private long updateCount = 0;


	public DemoAggregateState(long value) {
		this.value = value;
	}

	public DemoAggregateState() {
	}

}
