package com.evento.server.service.performance;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.server.domain.model.core.Handler;
import com.evento.server.domain.model.core.Payload;
import com.evento.server.domain.repository.core.HandlerRepository;
import com.evento.server.domain.repository.core.PayloadRepository;
import com.evento.server.performance.model.*;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.evento.common.performance.PerformanceService.SERVER;


/**
 * Builds the application's performance model: an open queueing network whose stations are the
 * handlers, the broker's gateway and the event store, and whose edges carry the mean number of
 * visits to the target per visit to the source.
 *
 * <p>A command handler's events (3.0.1): one event-store station per event type it may produce
 * ({@link Handler#emittedEvents()}), each reached with the measured mean number of events of
 * that type per command ({@link PerformanceStoreService#getProducedEventProbability}): below 1
 * for an event the handler produces only sometimes (a service answering without one, a
 * {@code DomainEvents} handler that returns it on some paths), above 1 for one it returns
 * several of. Before 3.0.1 every command handler had a single edge, to its return type, of
 * weight 1.
 *
 * <p>The model is a tree, unrolled from each source: a handler reached on two paths is two
 * stations. A handler that would reach itself again on the path being unrolled (a cycle in the
 * application's flows) is not expanded a second time.
 */
@Service
public class ApplicationPerformanceModelService {

    private final HandlerRepository handlerRepository;

    private final PerformanceStoreService performanceStoreService;
    private final PayloadRepository payloadRepository;

    /**
     * Constructs a new instance of the ApplicationPerformanceModelService class.
     *
     * @param handlerRepository       The handler repository.
     * @param performanceStoreService The performance store service.
     * @param payloadRepository       The payload repository.
     */
    public ApplicationPerformanceModelService(
            HandlerRepository handlerRepository, PerformanceStoreService performanceStoreService,
            PayloadRepository payloadRepository) {
        this.handlerRepository = handlerRepository;
        this.performanceStoreService = performanceStoreService;
        this.payloadRepository = payloadRepository;
    }

    /**
     * The whole application: one flow from each invoker.
     *
     * @return The PerformanceModel representation of the current instance.
     */
    public PerformanceModel toPerformanceModel() {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
            var source = n.source(i);

            var s = n.station(i, false, null);
            s.setPath(i.getComponent().getPath());
            s.setLines(i.getLine() == null ? List.of() : List.of(i.getLine()));

            manageInvocations(n, handlers, i, source, s, new HashSet<>());
        });

        return n;
    }

    private void manageInvocations(PerformanceModel n, List<Handler> handlers, Handler i, HasTarget source,
                                   ServiceStation s, Set<String> path) {
        source.addTarget(s, performanceStoreService);
        expandInvocations(n, handlers, i, s, path);
    }

    /** The commands and queries handler {@code i} sends, from its station {@code s}. */
    private void expandInvocations(PerformanceModel n, List<Handler> handlers, Handler i, ServiceStation s,
                                   Set<String> path) {
        if (!path.add(i.getUuid())) return;
        try {
            for (var p : new HashSet<>(i.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, p, s,
                        i.getUuid(),
                        i.getComponent().getPath(),
                        i.getComponent().getBundle().getLinePrefix(),
                        i.getInvocations().entrySet().stream()
                                .filter(e -> e.getValue().equals(p))
                                .map(Map.Entry::getKey).toList(),
                        path);
            }
        } finally {
            path.remove(i.getUuid());
        }
    }

    /**
     * Generates the invocation performance model for a given payload.
     *
     * @param n             The performance model to add the generated agents to.
     * @param handlers      The list of all handlers.
     * @param p             The payload to generate the performance model for.
     * @param source        The service station representing the source of the payload.
     * @param handlerId     The ID of the handler associated with the payload.
     * @param componentPath The path of the component associated with the handler.
     * @param lines         The line numbers of the handler invocations.
     * @param path          The handlers on the path being unrolled.
     */
    private void generateInvocationPerformanceModel(PerformanceModel n, List<Handler> handlers, Payload p, ServiceStation source,
                                                    String handlerId, String componentPath, String linePrefix, List<Integer> lines,
                                                    Set<String> path) {
        if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", "Gateway", p.getName(), p.getType().toString(), false, null, "Gateway_" + handlerId + "_" + p.getName(),
                    componentPath, linePrefix, lines);

            source.addTarget(serverRequestAgent, performanceStoreService);
            if (!p.getHandlers().isEmpty()) {
                // Server -> Component
                var handler = p.getHandlers().getFirst();
                var a = n.station(handler, false, null);
                expandInvocations(n, handlers, handler, a, path);
                serverRequestAgent.addTarget(a, performanceStoreService);
                // Component -> Server: the answer to the sender (the last event of a decision)
                var serverResponseAgent = n.station(SERVER, "Gateway", "Gateway", handler.getReturnType() == null ? "Void" : handler.getReturnType().getName(), handler.getReturnType() == null ? null : handler.getReturnType().getType().toString(), false, null, "Gateway_" + handler.getHandledPayload().toString() + "_" + (handler.getReturnType() == null ? "Void" : handler.getReturnType().getName())
                        , handler.getReturnType() == null ? null : handler.getReturnType().getPath(),
                        handler.getComponent().getBundle().getLinePrefix(),
                        (handler.getReturnType() == null || handler.getReturnType().getLine() == null) ? null : List.of(handler.getReturnType().getLine()));
                a.addTarget(serverResponseAgent, performanceStoreService);
                // Server -> ES -> consumers, one branch per event the handler may produce
                emitEvents(n, handlers, handler, serverResponseAgent, linePrefix, path);
            }

        } else if (p.getType() == PayloadType.Query) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", "Gateway", p.getName(), p.getType().toString(),
                    false, null, "Gateway_" + p.getName(), componentPath, linePrefix, lines);
            source.addTarget(serverRequestAgent, performanceStoreService);
            // Server -> Component
            if (!p.getHandlers().isEmpty()) {
                var handler = p.getHandlers().getFirst();
                var a = n.station(handler, false, null);
                expandInvocations(n, handlers, handler, a, path);
                serverRequestAgent.addTarget(a, performanceStoreService);
                // Component -> Server
                var serverResponseAgent = n.station(SERVER, "Gateway", "Gateway", handler.getReturnType().getName(),
                        handler.getReturnType().getType().toString(), false, null, "Gateway_" + handler.getReturnType().getName(),
                        null, linePrefix, null);
                a.addTarget(serverResponseAgent, performanceStoreService);
            }

        }
    }

    /**
     * The events command handler {@code handler} may produce, from station {@code from}: for each
     * type an event-store station, reached with the mean number of events of that type per
     * command, and from it every handler that consumes that type (event-sourcing handlers aside:
     * they run inside the aggregate's own station).
     */
    private void emitEvents(PerformanceModel n, List<Handler> handlers, Handler handler, ServiceStation from,
                            String linePrefix, Set<String> path) {
        var bundleId = handler.getComponent().getBundle().getId();
        var component = handler.getComponent().getComponentName();
        var command = handler.getHandledPayload().getName();
        for (var event : handler.emittedEvents()) {
            var esAgent = n.station("event-store", "EventStore", "EventStore", event.getName(),
                    event.getType() == null ? null : event.getType().toString(), false, null,
                    "EventStore_" + event.getName(), null, linePrefix, null);
            from.addTarget(esAgent, performanceStoreService.getProducedEventProbability(
                    bundleId, component, command, event.getName()));
            handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                    // By name: a handler reached through a lazy collection was loaded in another
                    // session, and Payload has no value equality
                    .filter(h -> h.getHandledPayload().getName().equals(event.getName())).forEach(h -> {
                        // ES -> EventHandler
                        var perf = performanceStoreService.getMeanServiceTime(h.getComponent().getBundle().getId(), h.getComponent().getComponentName(), h.getHandledPayload().getName());
                        var sum = 0.0;
                        for (var i : new HashSet<>(h.getInvocations().values())) {
                            if (i.getHandlers().isEmpty()) continue;
                            var ih = i.getHandlers().getFirst();
                            var st = performanceStoreService.getMeanServiceTime(ih.getComponent().getBundle().getId(), ih.getComponent().getComponentName(), ih.getHandledPayload().getName());
                            if (st != null)
                                sum += st;
                        }
                        perf = perf == null ? Math.max(1, sum) : perf;
                        var ha = n.station(h, true, h.getComponent().getComponentType() == ComponentType.Observer ? null : 1, perf);

                        manageInvocations(n, handlers, h, esAgent, ha, path);
                    });
        }
    }

    /**
     * The flow that starts at one handler.
     *
     * @param handlerId The ID of the handler to convert.
     * @return The PerformanceModel representation of the handler.
     */
    public PerformanceModel toPerformanceModel(String handlerId) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getUuid().equals(handlerId)).forEach(i -> {
            var path = new HashSet<String>();
            ServiceStation s = generateSourceFLow(n, handlers, i, path);
            emitEvents(n, handlers, i, s, i.getComponent().getBundle().getLinePrefix(), path);
        });

        return n;
    }

    /**
     * Generates the service station representing the source flow for a given handler in the performance model.
     *
     * @param n        The performance model to add the generated service station to.
     * @param handlers The list of all handlers.
     * @param i        The handler to generate the source flow for.
     * @return The generated service station representing the source flow.
     */
    private ServiceStation generateSourceFLow(PerformanceModel n, List<Handler> handlers, Handler i, Set<String> path) {
        var source = n.source(i);

        var s = n.station(i, false, null);

        manageInvocations(n, handlers, i, source, s, path);
        return s;
    }

    /**
     * The flows that start at the handlers of one payload.
     *
     * @param payload The payload to convert.
     * @return The PerformanceModel representation of the payload.
     */
    public PerformanceModel toPerformanceModelFromPayload(String payload) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();
        var p = payloadRepository.findById(payload).orElseThrow();
        var source = n.source(p.getName(), p.getType().toString());
        source.setPath(p.getPath());
        if (p.getLine() != null)
            source.setLines(List.of(p.getLine()));
        handlers.stream().filter(h -> h.getHandledPayload().getName().equals(payload) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {
            var path = new HashSet<String>();
            var s = n.station(i, false, null);
            manageInvocations(n, handlers, i, source, s, path);
            emitEvents(n, handlers, i, s, i.getComponent().getBundle().getLinePrefix(), path);
        });
        return n;
    }

    /**
     * The flows that start at the handlers of one component.
     *
     * @param component The name of the component.
     * @return The generated PerformanceModel object.
     */
    public PerformanceModel toPerformanceModelFromComponent(String component) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getComponent().getComponentName().equals(component) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {
            var path = new HashSet<String>();
            var s = generateSourceFLow(n, handlers, i, path);
            emitEvents(n, handlers, i, s, i.getComponent().getBundle().getLinePrefix(), path);
        });

        return n;
    }

    /**
     * The flows that start at the handlers of one bundle.
     *
     * @param bundle The ID of the bundle.
     * @return The PerformanceModel representation of the bundle.
     */
    public PerformanceModel toPerformanceModelFromBundle(String bundle) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getComponent().getBundle().getId().equals(bundle) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {
            var path = new HashSet<String>();
            var s = generateSourceFLow(n, handlers, i, path);
            emitEvents(n, handlers, i, s, i.getComponent().getBundle().getLinePrefix(), path);
        });

        return n;
    }
}
