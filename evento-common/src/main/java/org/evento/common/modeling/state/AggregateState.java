package org.evento.common.modeling.state;

import java.io.Serializable;

public abstract class AggregateState implements Serializable {
	private boolean deleted = false;

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}
}
