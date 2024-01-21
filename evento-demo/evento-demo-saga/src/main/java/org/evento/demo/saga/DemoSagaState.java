package org.evento.demo.saga;

import lombok.Getter;
import lombok.Setter;
import org.evento.common.modeling.state.SagaState;

@Setter
@Getter
public class DemoSagaState extends SagaState {
	private long lastValue;

}
