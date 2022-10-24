package org.eventrails.parser.model.node;

import java.io.Serializable;

public class Node implements Serializable {
	public String getComponentName() {
		return componentName;
	}

	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	private String componentName;
}
