package org.eventrails.demo.command.aggregate;

import org.eventrails.modeling.state.AggregateState;

public class DemoAggregateState extends AggregateState {
	private  long value;


	public DemoAggregateState(long value) {
		this.value = value;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}
}
