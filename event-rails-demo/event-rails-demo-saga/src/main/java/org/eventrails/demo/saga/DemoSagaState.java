package org.eventrails.demo.saga;

import org.eventrails.common.modeling.state.SagaState;

public class DemoSagaState extends SagaState {
	private long lastValue;

	public long getLastValue() {
		return lastValue;
	}

	public void setLastValue(long lastValue) {
		this.lastValue = lastValue;
	}
}
