package org.evento.common.modeling.state;

import java.io.Serializable;
import java.util.HashMap;

/**
 * An abstract class representing the state of a saga.
 */
public abstract class SagaState implements Serializable {
	private boolean ended = false;
	private final HashMap<String, String> associations = new HashMap<>();

	/**
	 * Determines if the saga is ended.
	 * @return true if the saga is ended, false otherwise
	 */
	public boolean isEnded() {
		return ended;
	}

	/**
	 * Sets the ended flag of the saga state.
	 *
	 * @param ended the value indicating if the saga is ended
	 */
	public void setEnded(boolean ended) {
		this.ended = ended;
	}

	/**
	 * Sets an association between an event field name and a value.
	 *
	 * @param eventFieldName the name of the event field
	 * @param value          the value to associate with the event field
	 */
	public void setAssociation(String eventFieldName, String value) {
		associations.put(eventFieldName, value);
	}
	/**
	 * Retrieves the value associated with the specified event field name.
	 *
	 * @param eventFieldName the name of the event field
	 * @return the value associated with the event field, or null if no association exists
	 */
	public String getAssociation(String eventFieldName) {
		return associations.get(eventFieldName);
	}

	/**
	 * Removes the association between an event field name and its value.
	 *
	 * @param eventFieldName the name of the event field to remove the association from
	 */
	public void unsetAssociation(String eventFieldName) {
		associations.remove(eventFieldName);
	}
}
