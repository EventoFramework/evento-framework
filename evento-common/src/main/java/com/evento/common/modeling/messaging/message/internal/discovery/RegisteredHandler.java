package com.evento.common.modeling.messaging.message.internal.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;

import java.io.Serializable;

/**
 * The RegisteredHandler class represents a handler that is registered in the system.
 * It contains information about the component type, component name, handler type, payload type, return type, and association property of the handler.
 *
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
 */
public class RegisteredHandler implements Serializable {

	private ComponentType componentType;
	private String componentName;

	private HandlerType handlerType;

	private PayloadType handledPayloadType;
	private String handledPayload;

	private String returnType;
	private boolean returnIsMultiple;
	private String associationProperty;

	/**
	 *
	 * Creates a new instance of the RegisteredHandler class.
	 *
	 * @param componentType The type of the component.
	 * @param componentName The name of the component.
	 * @param handlerType The type of the handler.
	 * @param handledPayloadType The type of the payload that the handler can handle.
	 * @param handledPayload The payload that the handler can handle.
	 * @param returnType The return type of the handler.
	 * @param returnIsMultiple Indicates if the handler can return multiple results.
	 * @param associationProperty The association property of the handler.
	 */
	public RegisteredHandler(ComponentType componentType, String componentName, HandlerType handlerType, PayloadType handledPayloadType, String handledPayload, String returnType, boolean returnIsMultiple, String associationProperty) {
		this.componentType = componentType;
		this.componentName = componentName;
		this.handlerType = handlerType;
		this.handledPayload = handledPayload;
		this.returnType = returnType;
		this.returnIsMultiple = returnIsMultiple;
		this.associationProperty = associationProperty;
		this.handledPayloadType = handledPayloadType;
	}

	/**
	 * The RegisteredHandler class represents a handler that is registered in the system.
	 * It encapsulates the information related to the handler, such as the component type, handler type, payload type, and other properties.
	 *
	 * @see ComponentType
	 * @see HandlerType
	 */
	public RegisteredHandler() {
	}


	/**
	 * Returns the {@link ComponentType} of the component.
	 *
	 * @return the {@link ComponentType} of the component.
	 */
	public ComponentType getComponentType() {
		return componentType;
	}

	/**
	 * Sets the component type of the RegisteredHandler.
	 *
	 * @param componentType The {@link ComponentType} to set for the RegisteredHandler.
	 */
	public void setComponentType(ComponentType componentType) {
		this.componentType = componentType;
	}

	/**
	 * Returns the name of the component.
	 *
	 * @return the name of the component
	 */
	public String getComponentName() {
		return componentName;
	}

	/**
	 * Sets the name of the component.
	 *
	 * @param componentName The name of the component to set.
	 */
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	/**
	 * Returns the type of the handler.
	 *
	 * @return the type of the handler.
	 */
	public HandlerType getHandlerType() {
		return handlerType;
	}

	/**
	 * Sets the handler type for the RegisteredHandler.
	 *
	 * @param handlerType The handler type to set for the RegisteredHandler.
	 * @see HandlerType
	 */
	public void setHandlerType(HandlerType handlerType) {
		this.handlerType = handlerType;
	}

	/**
	 * Returns the {@link PayloadType} of the payload that the handler can handle.
	 *
	 * @return the {@link PayloadType} of the payload that the handler can handle.
	 */
	public PayloadType getHandledPayloadType() {
		return handledPayloadType;
	}

	/**
	 * Sets the {@link PayloadType} of the payload that the handler can handle.
	 *
	 * @param handledPayloadType The {@link PayloadType} to set for the handler.
	 */
	public void setHandledPayloadType(PayloadType handledPayloadType) {
		this.handledPayloadType = handledPayloadType;
	}

	/**
	 * Retrieves the payload that the handler can handle.
	 *
	 * @return The handled payload.
	 */
	public String getHandledPayload() {
		return handledPayload;
	}

	/**
	 * Sets the handled payload for the RegisteredHandler.
	 *
	 * @param handledPayload The payload that the handler can handle.
	 *                       This is the payload type that the handler is capable of processing.
	 *                       It should be a string representation of the payload.
	 */
	public void setHandledPayload(String handledPayload) {
		this.handledPayload = handledPayload;
	}

	/**
	 * Retrieves the return type of the handler.
	 *
	 * @return the return type of the handler.
	 */
	public String getReturnType() {
		return returnType;
	}

	/**
	 * Sets the return type of the handler.
	 *
	 * @param returnType The return type to set for the handler.
	 */
	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	/**
	 * Returns the value of the returnIsMultiple field.
	 *
	 * @return the value of the returnIsMultiple field
	 */
	public boolean isReturnIsMultiple() {
		return returnIsMultiple;
	}

	/**
	 * Sets the value of the returnIsMultiple field.
	 *
	 * @param returnIsMultiple Indicates if the handler can return multiple results.
	 */
	public void setReturnIsMultiple(boolean returnIsMultiple) {
		this.returnIsMultiple = returnIsMultiple;
	}

	/**
	 * Retrieves the association property of the RegisteredHandler.
	 *
	 * @return The association property of the RegisteredHandler.
	 */
	public String getAssociationProperty() {
		return associationProperty;
	}

	/**
	 * Sets the association property of the .
	 *
	 * @param associationProperty The association property to set for the .
	 */
	public void setAssociationProperty(String associationProperty) {
		this.associationProperty = associationProperty;
	}

	@Override
	public String toString() {
		return "RegisteredHandler{" +
				"componentType=" + componentType +
				", componentName='" + componentName + '\'' +
				", handlerType=" + handlerType +
				", handledPayloadType=" + handledPayloadType +
				", handledPayload='" + handledPayload + '\'' +
				", returnType='" + returnType + '\'' +
				", returnIsMultiple=" + returnIsMultiple +
				", associationProperty='" + associationProperty + '\'' +
				'}';
	}
}
