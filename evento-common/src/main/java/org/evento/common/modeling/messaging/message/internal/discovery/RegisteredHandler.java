package org.evento.common.modeling.messaging.message.internal.discovery;

import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;

import java.io.Serializable;

public class RegisteredHandler implements Serializable {

    private ComponentType componentType;
    private String componentName;

    private HandlerType handlerType;

    private PayloadType handledPayloadType;
    private String handledPayload;

    private String returnType;
    private boolean returnIsMultiple;
    private String associationProperty;

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

    public RegisteredHandler() {
    }


    public ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public HandlerType getHandlerType() {
        return handlerType;
    }

    public void setHandlerType(HandlerType handlerType) {
        this.handlerType = handlerType;
    }

    public PayloadType getHandledPayloadType() {
        return handledPayloadType;
    }

    public void setHandledPayloadType(PayloadType handledPayloadType) {
        this.handledPayloadType = handledPayloadType;
    }

    public String getHandledPayload() {
        return handledPayload;
    }

    public void setHandledPayload(String handledPayload) {
        this.handledPayload = handledPayload;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public boolean isReturnIsMultiple() {
        return returnIsMultiple;
    }

    public void setReturnIsMultiple(boolean returnIsMultiple) {
        this.returnIsMultiple = returnIsMultiple;
    }

    public String getAssociationProperty() {
        return associationProperty;
    }

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
