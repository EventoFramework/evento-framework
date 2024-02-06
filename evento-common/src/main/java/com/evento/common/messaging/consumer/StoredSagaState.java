package com.evento.common.messaging.consumer;

import com.evento.common.modeling.state.SagaState;

/**
 * This class represents the stored state of a Saga.
 */
public class StoredSagaState {
	private Long id;
	private SagaState state;

	/**
	 * Represents the stored state of a Saga.
     * @param id unique state identifier
     * @param state the saga state
     */
	public StoredSagaState(Long id, SagaState state) {
		this.id = id;
		this.state = state;
	}

	/**
	 * Constructs a new instance of StoredSagaState.
	 */
	public StoredSagaState() {
	}

	/**
	 * Retrieves the ID of the stored saga state.
	 *
	 * @return the ID of the stored saga state
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Sets the ID of the stored saga state.
	 *
	 * @param id the ID to set
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * Retrieves the current state of the saga.
	 *
	 * @return the current state of the saga
	 */
	public SagaState getState() {
		return state;
	}

	/**
	 * Set the state of the saga.
	 *
	 * @param state the new state to set
	 */
	public void setState(SagaState state) {
		this.state = state;
	}
}
