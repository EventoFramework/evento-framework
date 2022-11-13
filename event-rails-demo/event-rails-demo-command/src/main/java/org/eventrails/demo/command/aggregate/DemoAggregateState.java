package org.eventrails.demo.command.aggregate;

import org.eventrails.common.modeling.state.AggregateState;

public class DemoAggregateState extends AggregateState {
	private  long value;


	public DemoAggregateState(long value) {
		this.value = value;
	}

	public DemoAggregateState() {
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}
}
