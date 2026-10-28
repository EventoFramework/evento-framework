package com.evento.common.modeling.messaging.message.internal.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
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

    /**
     * The events a command handler may produce (3.0.1): for an aggregate handler returning
     * {@code DomainEvents} the classes in {@code produces}, otherwise its return type. The
     * catalog draws an edge for each, and the performance model weighs each by how often the
     * handler produces it. Left out of the wire when empty; a broker before 3.0.1 ignores it
     * (unknown properties are skipped since 2.4.0) and keeps drawing the return type alone.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> producedEvents = List.of();

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

    /**
     * Name of the {@code ConsumerExecutor} this handler is dispatched to
     * ({@code @EventHandler(executor = "...")}), or {@code ""} when it runs inline on the
     * consumer's own thread.
     *
     * <p>Surfaced so the dashboard can show which handlers consume in parallel — that
     * changes their delivery and ordering guarantees, and is otherwise invisible from
     * outside the bundle.
     */
    private String executor = "";

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

    /**
     * This handler declared for another payload name: a former name that an upcaster renames to
     * the one this handler takes (3.0), so the broker routes and treats it the same way.
     */
    public RegisteredHandler forPayload(String payloadName) {
        var copy = new RegisteredHandler();
        copy.componentType = componentType;
        copy.componentName = componentName;
        copy.handlerType = handlerType;
        copy.handledPayloadType = handledPayloadType;
        copy.handledPayload = payloadName;
        copy.returnType = returnType;
        copy.returnIsMultiple = returnIsMultiple;
        copy.associationProperty = associationProperty;
        copy.producedEvents = producedEvents;
        copy.componentDescription = componentDescription;
        copy.componentDetail = componentDetail;
        copy.componentPath = componentPath;
        copy.componentLine = componentLine;
        copy.handlerLine = handlerLine;
        copy.executor = executor;
        copy.invokedCommands = invokedCommands;
        copy.invokedQueries = invokedQueries;
        return copy;
    }

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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getProducedEvents() { return producedEvents; }
    public void setProducedEvents(List<String> producedEvents) {
        this.producedEvents = producedEvents == null ? List.of() : List.copyOf(producedEvents);
    }


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

    public String getExecutor() { return executor; }
    public void setExecutor(String executor) {
        this.executor = executor == null ? "" : executor;
    }

    // NOTE: do not add derived getters (e.g. an `isAsync()`) to this class. It is a wire DTO:
    // Jackson would put the derived property on the wire. Peers since 2.4.0 skip unknown
    // properties (JacksonCborPayloadCodec), but an older broker refused the whole
    // BundleDiscoveryInfo, and the failure was near-invisible: the bundle kept running and
    // only its handler metadata disappeared from the dashboard. Compute such predicates at
    // the call site instead.

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
                ", producedEvents=" + producedEvents +
                ", componentPath='" + componentPath + '\'' +
                ", componentLine=" + componentLine +
                ", handlerLine=" + handlerLine +
                ", executor='" + executor + '\'' +
                ", invokedCommands=" + invokedCommands +
                ", invokedQueries=" + invokedQueries +
                '}';
    }
}
