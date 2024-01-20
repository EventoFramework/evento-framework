package org.evento.common.modeling.state;

import java.io.Serializable;

/**
 * The AggregateState class represents the state of an aggregate.
 * It provides the functionality to mark the state as deleted.
 */
public abstract class AggregateState implements Serializable {
	private boolean deleted = false;

	/**
	 * Determines whether the aggregate state is marked as deleted.
	 *
	 * @return {@code true} if the aggregate state is marked as deleted, {@code false} otherwise.
	 */
	public boolean isDeleted() {
		return deleted;
	}

	/**
	 * Sets the deleted flag of the aggregate state.
	 *
	 * @param deleted {@code true} to mark the aggregate state as deleted, {@code false} otherwise.
	 */
	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}
}
