package org.eventrails.common.modeling.messaging.message.internal.processor;

import org.eventrails.common.modeling.bundle.types.ComponentType;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;

import java.io.Serializable;

public class FetchEventRequest implements Serializable {

	private ComponentType componentType;
	private String componentName;

	public FetchEventRequest() {
	}

	public FetchEventRequest(ComponentType componentType, String componentName) {
		this.componentType = componentType;
		this.componentName = componentName;
	}

	public String getComponentName() {
		return componentName;
	}

	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	public ComponentType getComponentType() {
		return componentType;
	}

	public void setComponentType(ComponentType componentType) {
		this.componentType = componentType;
	}
}
