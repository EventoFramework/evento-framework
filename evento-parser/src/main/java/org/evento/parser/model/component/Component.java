package org.evento.parser.model.component;

import java.io.Serializable;

public abstract class Component implements Serializable {
	public String getComponentName() {
		return componentName;
	}

	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	private String componentName;
}
