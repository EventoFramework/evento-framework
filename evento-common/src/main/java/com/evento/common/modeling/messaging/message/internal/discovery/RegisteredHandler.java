package com.evento.common.modeling.messaging.message.internal.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;

import java.io.Serializable;
import java.util.Map;

/**
 * Wire-protocol DTO describing one handler registered by a bundle.
 *
 * <p>Carries both routing data (component/handler/payload types) and rich
 * discovery metadata (source path, line numbers, description) so the server
 * can build the full application graph and dashboard view without the
 * {@code evento-cli} static-analysis step.
 *
 * @see <a href="https://docs.eventoframework.com/recq-patterns/recq-component-pattern">RECQ Component Pattern</a>
 */
public class RegisteredHandler implements Serializable {

    // ── routing fields ─────────────────────────────────────────────────────────

    private ComponentType componentType;
    private String componentName;

    private HandlerType handlerType;

    private PayloadType handledPayloadType;
    private String handledPayload;

    private String returnType;
    private boolean returnIsMultiple;
    private String associationProperty;

    // ── component source metadata ───────────────────────────────────────────────

    /** Short description from {@code @EventoDescription.value()}, or class simple name. */
    private String componentDescription = "";
    /** Markdown long-form from {@code @EventoDescription.detail()}, or "". */
    private String componentDetail = "";
    /**
     * Relative source path of the component class file,
     * e.g. {@code com/example/order/OrderAggregate.java}.
     */
    private String componentPath = "";
    /** Approximate line of the class declaration (from {@code <init>} LineNumberTable). */
    private int componentLine = 0;

    // ── handler source metadata ─────────────────────────────────────────────────

    /** Line of the handler method declaration inside its source file. */
    private int handlerLine = 0;

    // ── invocation edges ────────────────────────────────────────────────────────

    /**
     * Gateway invocations detected by ASM analysis: source line → Command simple name.
     * Keys are the source line at the call site; values are payload simple class names.
     */
    private Map<Integer, String> invokedCommands = Map.of();

    /**
     * Gateway invocations detected by ASM analysis: source line → Query simple name.
     * Keys are the source line at the call site; values are payload simple class names.
     */
    private Map<Integer, String> invokedQueries = Map.of();

    // ── constructors ────────────────────────────────────────────────────────────

    public RegisteredHandler(ComponentType componentType, String componentName,
                             HandlerType handlerType, PayloadType handledPayloadType,
                             String handledPayload, String returnType,
                             boolean returnIsMultiple, String associationProperty) {
        this.componentType      = componentType;
        this.componentName      = componentName;
        this.handlerType        = handlerType;
        this.handledPayload     = handledPayload;
        this.returnType         = returnType;
        this.returnIsMultiple   = returnIsMultiple;
        this.associationProperty = associationProperty;
        this.handledPayloadType = handledPayloadType;
    }

    public RegisteredHandler() {}

    // ── routing getters/setters ─────────────────────────────────────────────────

    public ComponentType getComponentType() { return componentType; }
    public void setComponentType(ComponentType componentType) { this.componentType = componentType; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public HandlerType getHandlerType() { return handlerType; }
    public void setHandlerType(HandlerType handlerType) { this.handlerType = handlerType; }

    public PayloadType getHandledPayloadType() { return handledPayloadType; }
    public void setHandledPayloadType(PayloadType handledPayloadType) { this.handledPayloadType = handledPayloadType; }

    public String getHandledPayload() { return handledPayload; }
    public void setHandledPayload(String handledPayload) { this.handledPayload = handledPayload; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public boolean isReturnIsMultiple() { return returnIsMultiple; }
    public void setReturnIsMultiple(boolean returnIsMultiple) { this.returnIsMultiple = returnIsMultiple; }

    public String getAssociationProperty() { return associationProperty; }
    public void setAssociationProperty(String associationProperty) { this.associationProperty = associationProperty; }

    // ── component metadata getters/setters ──────────────────────────────────────

    public String getComponentDescription() { return componentDescription; }
    public void setComponentDescription(String componentDescription) {
        this.componentDescription = componentDescription == null ? "" : componentDescription;
    }

    public String getComponentDetail() { return componentDetail; }
    public void setComponentDetail(String componentDetail) {
        this.componentDetail = componentDetail == null ? "" : componentDetail;
    }

    public String getComponentPath() { return componentPath; }
    public void setComponentPath(String componentPath) {
        this.componentPath = componentPath == null ? "" : componentPath;
    }

    public int getComponentLine() { return componentLine; }
    public void setComponentLine(int componentLine) { this.componentLine = componentLine; }

    // ── handler metadata getters/setters ────────────────────────────────────────

    public int getHandlerLine() { return handlerLine; }
    public void setHandlerLine(int handlerLine) { this.handlerLine = handlerLine; }

    // ── invocation getters/setters ──────────────────────────────────────────────

    public Map<Integer, String> getInvokedCommands() { return invokedCommands; }
    public void setInvokedCommands(Map<Integer, String> invokedCommands) {
        this.invokedCommands = invokedCommands == null ? Map.of() : invokedCommands;
    }

    public Map<Integer, String> getInvokedQueries() { return invokedQueries; }
    public void setInvokedQueries(Map<Integer, String> invokedQueries) {
        this.invokedQueries = invokedQueries == null ? Map.of() : invokedQueries;
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
                ", componentPath='" + componentPath + '\'' +
                ", componentLine=" + componentLine +
                ", handlerLine=" + handlerLine +
                ", invokedCommands=" + invokedCommands +
                ", invokedQueries=" + invokedQueries +
                '}';
    }
}
