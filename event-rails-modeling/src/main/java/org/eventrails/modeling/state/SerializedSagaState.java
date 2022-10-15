package org.eventrails.modeling.state;

import org.eventrails.modeling.common.SerializedObject;

public class SerializedSagaState<T extends SagaState> extends SerializedObject<T> {


	private boolean isEnded;
	public SerializedSagaState(T sagaState) {
		super(sagaState);
		if(sagaState == null)
		{
			isEnded = false;
		}else{
			this.isEnded = sagaState.isEnded();
		}
	}

	public SerializedSagaState() {
	}

	public SagaState getSagaState() {
		return getObject();
	}

	public boolean isEnded() {
		return isEnded;
	}

	public void setEnded(boolean ended) {
		isEnded = ended;
	}
}
