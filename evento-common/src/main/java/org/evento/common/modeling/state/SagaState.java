package org.evento.common.modeling.state;

import java.io.Serializable;
import java.util.HashMap;

public abstract class SagaState implements Serializable {
	private boolean ended = false;
	private final HashMap<String, String> associations = new HashMap<>();

	public boolean isEnded() {
		return ended;
	}

	public void setEnded(boolean ended) {
		this.ended = ended;
	}

	public void setAssociation(String eventFieldName, String value) {
		associations.put(eventFieldName, value);
	}
	public String getAssociation(String eventFieldName) {
		return associations.get(eventFieldName);
	}

	public void unsetAssociation(String eventFieldName) {
		associations.remove(eventFieldName);
	}
}
