package com.evento.demo.saga;

import lombok.Getter;
import lombok.Setter;
import com.evento.common.modeling.state.SagaState;

@Setter
@Getter
public class DemoSagaState extends SagaState {
	private long lastValue;

}
