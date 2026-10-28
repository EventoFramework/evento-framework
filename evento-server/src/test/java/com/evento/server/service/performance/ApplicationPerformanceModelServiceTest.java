package com.evento.server.service.performance;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Handler;
import com.evento.server.domain.model.core.Payload;
import com.evento.server.domain.repository.core.HandlerRepository;
import com.evento.server.domain.repository.core.PayloadRepository;
import com.evento.server.performance.model.Node;
import com.evento.server.performance.model.PerformanceModel;
import com.evento.server.performance.model.ServiceStation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The performance model of command handlers that produce several events, or an event only
 * sometimes (3.0.1): one event-store branch per declared event, weighted by the measured mean
 * number of events of that type per command, and a handler registered before 3.0.1 still
 * modelled by its return type.
 */
class ApplicationPerformanceModelServiceTest {

    private final HandlerRepository handlerRepository = mock(HandlerRepository.class);
    private final PayloadRepository payloadRepository = mock(PayloadRepository.class);
    private final PerformanceStoreService store = mock(PerformanceStoreService.class);
    private final ApplicationPerformanceModelService service =
            new ApplicationPerformanceModelService(handlerRepository, store, payloadRepository);

    private final Bundle bundle = new Bundle();
    private final List<Handler> handlers = new ArrayList<>();
    private final Map<String, Payload> payloads = new HashMap<>();

    @BeforeEach
    void setUp() {
        bundle.setId("shop");
        bundle.setLinePrefix("L");
        when(handlerRepository.findAll()).thenReturn(handlers);
        when(store.getMeanServiceTime(anyString(), anyString(), anyString())).thenReturn(null);
        when(store.getInvocationProbability(anyString(), anyString(), anyString(), anyString())).thenReturn(1.0);
        when(store.getProducedEventProbability(anyString(), anyString(), anyString(), anyString())).thenReturn(1.0);
    }

    @Test
    void eachDeclaredEventIsABranchWeightedByHowManyOfItACommandProduces() {
        var checkOut = handler("Basket", ComponentType.Aggregate, HandlerType.AggregateCommandHandler,
                "CheckOut", PayloadType.DomainCommand, "ItemReserved",
                Set.of("ItemReserved", "BasketCheckedOut", "CouponUsed"), Map.of());
        handler("Stock", ComponentType.Projector, HandlerType.EventHandler, "ItemReserved", PayloadType.DomainEvent,
                null, Set.of(), Map.of());
        handler("Loyalty", ComponentType.Projector, HandlerType.EventHandler, "CouponUsed", PayloadType.DomainEvent,
                null, Set.of(), Map.of());
        invoker("CheckOut");
        when(store.getProducedEventProbability("shop", "Basket", "CheckOut", "ItemReserved")).thenReturn(3.0);
        when(store.getProducedEventProbability("shop", "Basket", "CheckOut", "CouponUsed")).thenReturn(0.25);

        var model = service.toPerformanceModel();

        var response = station(model, "Gateway", "ItemReserved");   // the answer, then the events
        assertThat(weights(response)).containsOnly(
                Map.entry("EventStore:ItemReserved", 3.0),
                Map.entry("EventStore:BasketCheckedOut", 1.0),
                Map.entry("EventStore:CouponUsed", 0.25));
        assertThat(weights(station(model, "EventStore", "ItemReserved"))).containsOnlyKeys("Stock:ItemReserved");
        assertThat(weights(station(model, "EventStore", "CouponUsed"))).containsOnlyKeys("Loyalty:CouponUsed");
        assertThat(weights(station(model, "EventStore", "BasketCheckedOut"))).isEmpty();
        assertThat(checkOut.emittedEvents()).extracting(Payload::getName)
                .containsExactly("BasketCheckedOut", "CouponUsed", "ItemReserved");
    }

    @Test
    void aServiceThatMayAnswerWithoutAnEventGetsItsMeasuredProbability() {
        handler("Notifier", ComponentType.Service, HandlerType.CommandHandler, "Notify", PayloadType.ServiceCommand,
                "Notified", Set.of("Notified"), Map.of());
        handler("Outbox", ComponentType.Projector, HandlerType.EventHandler, "Notified", PayloadType.ServiceEvent,
                null, Set.of(), Map.of());
        invoker("Notify");
        when(store.getProducedEventProbability("shop", "Notifier", "Notify", "Notified")).thenReturn(0.4);

        var model = service.toPerformanceModel();

        assertThat(weights(station(model, "Gateway", "Notified"))).containsOnly(Map.entry("EventStore:Notified", 0.4));
    }

    @Test
    void aHandlerRegisteredBeforeProducedEventsIsModelledByItsReturnType() {
        handler("Basket", ComponentType.Aggregate, HandlerType.AggregateCommandHandler,
                "CreateBasket", PayloadType.DomainCommand, "BasketCreated", Set.of(), Map.of());
        handler("Stock", ComponentType.Projector, HandlerType.EventHandler, "BasketCreated", PayloadType.DomainEvent,
                null, Set.of(), Map.of());
        invoker("CreateBasket");

        var model = service.toPerformanceModel();

        assertThat(weights(station(model, "Gateway", "BasketCreated"))).containsOnly(Map.entry("EventStore:BasketCreated", 1.0));
        assertThat(weights(station(model, "EventStore", "BasketCreated"))).containsOnlyKeys("Stock:BasketCreated");
    }

    @Test
    void aQueryHandlersViewIsNotAnEvent() {
        handler("Catalog", ComponentType.Projection, HandlerType.QueryHandler, "FindBasket", PayloadType.Query,
                "BasketView", Set.of(), Map.of());

        assertThat(handlers.getFirst().emittedEvents()).isEmpty();
        var model = service.toPerformanceModelFromComponent("Catalog");
        assertThat(model.getNodes()).noneMatch(n -> n instanceof ServiceStation s && "EventStore".equals(s.getComponent()));
    }

    @Test
    void aFlowThatLeadsBackToItsOwnCommandIsUnrolledOnceNotForEver() {
        handler("Basket", ComponentType.Aggregate, HandlerType.AggregateCommandHandler,
                "CheckOut", PayloadType.DomainCommand, "BasketCheckedOut", Set.of("BasketCheckedOut", "ItemReserved"), Map.of());
        // An observer that checks the basket out again: a cycle through a produced event
        handler("Retrier", ComponentType.Observer, HandlerType.EventHandler, "ItemReserved", PayloadType.DomainEvent,
                null, Set.of(), Map.of(7, "CheckOut"));
        invoker("CheckOut");

        var model = service.toPerformanceModel();

        var observers = model.getNodes().stream()
                .filter(n -> n instanceof ServiceStation s && "Retrier".equals(s.getComponent())).count();
        assertThat(observers).isBetween(1L, 2L);
        assertThat(model.getNodes().size()).isLessThan(40);
    }

    @Test
    void theFlowOfOneComponentHasTheSameBranches() {
        handler("Basket", ComponentType.Aggregate, HandlerType.AggregateCommandHandler,
                "CheckOut", PayloadType.DomainCommand, "ItemReserved", Set.of("ItemReserved", "CouponUsed"), Map.of());
        when(store.getProducedEventProbability("shop", "Basket", "CheckOut", "CouponUsed")).thenReturn(0.1);

        var model = service.toPerformanceModelFromComponent("Basket");

        var handlerStation = model.getNodes().stream()
                .filter(n -> n instanceof ServiceStation s && "Basket".equals(s.getComponent()))
                .map(n -> (ServiceStation) n).findFirst().orElseThrow();
        assertThat(weights(handlerStation)).containsOnly(
                Map.entry("EventStore:ItemReserved", 1.0),
                Map.entry("EventStore:CouponUsed", 0.1));
    }

    // ── fixture ─────────────────────────────────────────────────────────────────

    private Payload payload(String name, PayloadType type) {
        return payloads.computeIfAbsent(name, n -> {
            var p = new Payload();
            p.setName(n);
            p.setType(type);
            p.setHandlers(new ArrayList<>());
            return p;
        });
    }

    private Handler handler(String component, ComponentType componentType, HandlerType handlerType,
                            String handled, PayloadType handledType, String returnType,
                            Set<String> produced, Map<Integer, String> invocations) {
        var c = new Component();
        c.setId(bundle.getId() + ":" + component);
        c.setComponentName(component);
        c.setComponentType(componentType);
        c.setBundle(bundle);
        var h = new Handler();
        h.setComponent(c);
        h.setHandlerType(handlerType);
        h.setHandledPayload(payload(handled, handledType));
        var eventType = componentType == ComponentType.Service ? PayloadType.ServiceEvent : PayloadType.DomainEvent;
        if (returnType != null) {
            h.setReturnType(payload(returnType, componentType == ComponentType.Projection ? PayloadType.View : eventType));
        }
        var producedEvents = new HashSet<Payload>();
        produced.forEach(name -> producedEvents.add(payload(name, eventType)));
        h.setProducedEvents(producedEvents);
        var inv = new HashMap<Integer, Payload>();
        invocations.forEach((line, name) -> inv.put(line, payload(name, PayloadType.DomainCommand)));
        h.setInvocations(inv);
        h.setUuid(component + ":" + handled);
        if (handlerType != HandlerType.EventSourcingHandler) h.getHandledPayload().getHandlers().add(h);
        handlers.add(h);
        return h;
    }

    private void invoker(String command) {
        handler("Api", ComponentType.Invoker, HandlerType.InvocationHandler, "Api" + command, PayloadType.Invocation,
                null, Set.of(), Map.of(1, command));
    }

    private static ServiceStation station(PerformanceModel model, String component, String action) {
        return model.getNodes().stream()
                .filter(n -> n instanceof ServiceStation s && component.equals(s.getComponent()) && action.equals(s.getAction()))
                .map(n -> (ServiceStation) n)
                .findFirst().orElseThrow(() -> new AssertionError("no station " + component + ":" + action));
    }

    private static Map<String, Double> weights(ServiceStation s) {
        var out = new HashMap<String, Double>();
        for (Map.Entry<Node, Double> e : s.getTarget().entrySet()) {
            if (e.getKey() instanceof ServiceStation t) out.put(t.getComponent() + ":" + t.getAction(), e.getValue());
        }
        return out;
    }
}
