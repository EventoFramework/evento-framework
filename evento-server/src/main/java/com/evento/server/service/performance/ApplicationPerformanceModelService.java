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

import static com.evento.common.performance.PerformanceService.SERVER;


/**
 * The ApplicationPerformanceModelService class represents a service for generating application performance models.
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
     * Converts the current instance to a PerformanceModel object.
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

            manageInvocations(n, handlers, i, source, s);

        });

        return n;

    }

    private void manageInvocations(PerformanceModel n, List<Handler> handlers, Handler i, HasTarget source, ServiceStation s) {
        source.addTarget(s, performanceStoreService);


        for (var p : new HashSet<>(i.getInvocations().values())) {
            generateInvocationPerformanceModel(n, handlers, p, s,
                    i.getUuid(),
                    i.getComponent().getPath(),
                    i.getInvocations().entrySet().stream()
                            .filter(e -> e.getValue().equals(p))
                            .map(Map.Entry::getKey).toList());
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
     */
    private void generateInvocationPerformanceModel(PerformanceModel n, List<Handler> handlers, Payload p, ServiceStation source,
                                                    String handlerId, String componentPath, List<Integer> lines) {
        if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", "Gateway", p.getName(), p.getType().toString(), false, null, "Gateway_" + handlerId + "_" + p.getName(),
                    componentPath, lines);

            source.addTarget(serverRequestAgent, performanceStoreService);
            if (!p.getHandlers().isEmpty()) {
                // Server -> Component
                var handler = p.getHandlers().get(0);
                var a = n.station(handler, false, null);
                for (var pp : new HashSet<>(handler.getInvocations().values())) {
                    generateInvocationPerformanceModel(n, handlers, pp, a,
                            handler.getUuid(),
                            handler.getComponent().getPath(),
                            handler.getInvocations().entrySet().stream()
                                    .filter(e -> e.getValue().equals(pp))
                                    .map(Map.Entry::getKey).toList());
                }
                serverRequestAgent.addTarget(a, performanceStoreService);
                // Component -> Server
                var serverResponseAgent = n.station(SERVER, "Gateway", "Gateway", handler.getReturnType() == null ? "Void" : handler.getReturnType().getName(), handler.getReturnType() == null ? null : handler.getReturnType().getType().toString(), false, null, "Gateway_" + handler.getHandledPayload().toString() + "_" + (handler.getReturnType() == null ? "Void" : handler.getReturnType().getName())
                        , handler.getReturnType() == null ? null : handler.getReturnType().getPath(),
                        (handler.getReturnType() == null || handler.getReturnType().getLine() == null) ? null : List.of(handler.getReturnType().getLine()));
                a.addTarget(serverResponseAgent, performanceStoreService);
                if (handler.getReturnType() != null) {
                    // Server -> ES
                    var esAgent = n.station("event-store", "EventStore", "EventStore", handler.getReturnType().getName(), handler.getReturnType().getType().toString(), false, null, "EventStore_" + handler.getReturnType().getName(),
                            null, null);
                    serverResponseAgent.addTarget(esAgent, performanceStoreService);
                    handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                            .filter(h -> h.getHandledPayload().equals(handler.getReturnType())).forEach(h -> {
                                // ES -> EventHandler
                                var perf = performanceStoreService.getMeanServiceTime(h.getComponent().getBundle().getId(), h.getComponent().getComponentName(), h.getHandledPayload().getName());
                                var sum = 0.0;
                                for (var i : new HashSet<>(h.getInvocations().values())) {
                                    var ih = i.getHandlers().get(0);
                                    var st = performanceStoreService.getMeanServiceTime(ih.getComponent().getBundle().getId(), ih.getComponent().getComponentName(), ih.getHandledPayload().getName());
                                    if (st != null)
                                        sum += st;
                                }
                                perf = perf == null ? null : Math.max(perf, sum);
                                var ha = n.station(h, true, h.getComponent().getComponentType() == ComponentType.Observer ? null : 1, perf);

                                manageInvocations(
                                        n,
                                        handlers,
                                        h,
                                        esAgent,
                                        ha
                                );
                            });
                }
            }

        } else if (p.getType() == PayloadType.Query) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", "Gateway", p.getName(), p.getType().toString(),
                    false, null, "Gateway_" + p.getName(), componentPath, lines);
            source.addTarget(serverRequestAgent, performanceStoreService);
            // Server -> Component
            if (!p.getHandlers().isEmpty()) {
                var handler = p.getHandlers().get(0);
                var a = n.station(handler, false, null);
                for (var pp : new HashSet<>(handler.getInvocations().values())) {
                    generateInvocationPerformanceModel(n, handlers, pp, a,
                            handler.getUuid(),
                            handler.getComponent().getPath(),
                            handler.getInvocations().entrySet().stream()
                                    .filter(e -> e.getValue().equals(pp))
                                    .map(Map.Entry::getKey).toList());
                }
                serverRequestAgent.addTarget(a, performanceStoreService);
                // Component -> Server
                var serverResponseAgent = n.station(SERVER, "Gateway", "Gateway", handler.getReturnType().getName(),
                        handler.getReturnType().getType().toString(), false, null, "Gateway_" + handler.getReturnType().getName(),
                        null, null);
                a.addTarget(serverResponseAgent, performanceStoreService);
            }

        }
    }

    /**
     * Converts the given handler to a PerformanceModel object.
     *
     * @param handlerId The ID of the handler to convert.
     * @return The PerformanceModel representation of the handler.
     */
    public PerformanceModel toPerformanceModel(String handlerId) {

        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getUuid().equals(handlerId)).forEach(i -> {


            ServiceStation s = generateSourceFLow(n, handlers, i);


            if (i.getReturnType() != null) {

                // Server -> ES
                var esAgent = n.station("event-store", "EventStore", "EventStore", i.getReturnType().getName()
                        , i.getHandledPayload().getType().toString(), false, null, "EventStore_" + i.getReturnType().getName(),
                        null, null);
                s.addTarget(esAgent, performanceStoreService);

                // ES -> Invoker
                handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                        .filter(h -> h.getHandledPayload().equals(i.getReturnType())).forEach(h -> {
                            // ES -> EventHandler
                            var ha = n.station(h,
                                    true, h.getComponent().getComponentType() == ComponentType.Observer ? null : 1);
                            esAgent.addTarget(ha, performanceStoreService);

                            for (var j : new HashSet<>(h.getInvocations().values())) {
                                generateInvocationPerformanceModel(n, handlers, j, ha,
                                        h.getUuid(),
                                        h.getComponent().getPath(),
                                        h.getInvocations().entrySet().stream()
                                                .filter(e -> e.getValue().equals(j))
                                                .map(Map.Entry::getKey).toList());
                            }
                        });
            }


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
    private ServiceStation generateSourceFLow(PerformanceModel n, List<Handler> handlers, Handler i) {
        var source = n.source(i);

        var s = n.station(i, false, null);

        manageInvocations(n, handlers, i, source, s);
        return s;
    }

    /**
     * Converts the payload to a PerformanceModel object.
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

            var s = n.station(i, false, null);

            manageInvocations(n, handlers, i, source, s);


            if (i.getReturnType() != null && i.getHandlerType() != HandlerType.QueryHandler) {

                // Server -> ES
                var esAgent = n.station("event-store", "EventStore", "EventStore", i.getReturnType().getName()
                        , i.getHandledPayload().getType().toString(), false, null, "EventStore_" + i.getReturnType().getName(),
                        null, null);
                s.addTarget(esAgent, performanceStoreService);

                // ES -> Invoker
                handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                        .filter(h -> h.getHandledPayload().equals(i.getReturnType())).forEach(h -> {
                            // ES -> EventHandler
                            var ha = n.station(h,
                                    true, h.getComponent().getComponentType() == ComponentType.Observer ? null : 1);
                            esAgent.addTarget(ha, performanceStoreService);

                            for (var j : new HashSet<>(h.getInvocations().values())) {
                                generateInvocationPerformanceModel(n, handlers, j, ha,
                                        h.getUuid(),
                                        h.getComponent().getPath(),
                                        h.getInvocations().entrySet().stream()
                                                .filter(e -> e.getValue().equals(j))
                                                .map(Map.Entry::getKey).toList());
                            }
                        });
            }


        });
        return n;
    }

    /**
     * Generates a PerformanceModel object from a given component.
     *
     * @param component The name of the component.
     * @return The generated PerformanceModel object.
     */
    public PerformanceModel toPerformanceModelFromComponent(String component) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getComponent().getComponentName().equals(component) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {

            var s = generateSourceFLow(n, handlers, i);


            if (i.getReturnType() != null && i.getHandlerType() != HandlerType.QueryHandler) {

                // Server -> ES
                var esAgent = n.station("event-store", "EventStore", "EventStore", i.getReturnType().getName()
                        , i.getHandledPayload().getType().toString(), false, null, "EventStore_" + i.getReturnType().getName(),
                        null, null);
                s.addTarget(esAgent, performanceStoreService);

                // ES -> Invoker
                handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                        .filter(h -> h.getHandledPayload().equals(i.getReturnType())).forEach(h -> {
                            // ES -> EventHandler
                            var ha = n.station(h,
                                    true, h.getComponent().getComponentType() == ComponentType.Observer ? null : 1);
                            esAgent.addTarget(ha, performanceStoreService);

                            for (var j : new HashSet<>(h.getInvocations().values())) {
                                generateInvocationPerformanceModel(n, handlers, j, ha,
                                        h.getUuid(),
                                        h.getComponent().getPath(),
                                        h.getInvocations().entrySet().stream()
                                                .filter(e -> e.getValue().equals(j))
                                                .map(Map.Entry::getKey).toList());
                            }
                        });
            }


        });

        return n;
    }

    /**
     * Converts the given bundle to a PerformanceModel object.
     *
     * @param bundle The ID of the bundle.
     * @return The PerformanceModel representation of the bundle.
     */
    public PerformanceModel toPerformanceModelFromBundle(String bundle) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getComponent().getBundle().getId().equals(bundle) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {

            var s = generateSourceFLow(n, handlers, i);


            if (i.getReturnType() != null && i.getHandlerType() != HandlerType.QueryHandler) {

                // Server -> ES
                var esAgent = n.station("event-store", "EventStore", "EventStore", i.getReturnType().getName()
                        , i.getHandledPayload().getType().toString(), false, null, "EventStore_" + i.getReturnType().getName(),
                        null, null);
                s.addTarget(esAgent, performanceStoreService);

                // ES -> Invoker
                handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                        .filter(h -> h.getHandledPayload().equals(i.getReturnType())).forEach(h -> {
                            // ES -> EventHandler
                            var ha = n.station(h,
                                    true, h.getComponent().getComponentType() == ComponentType.Observer ? null : 1);
                            esAgent.addTarget(ha, performanceStoreService);

                            for (var j : new HashSet<>(h.getInvocations().values())) {
                                //var iq = n.station(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName() + " [" + j.getKey() + "]", false, null);
                                generateInvocationPerformanceModel(n, handlers, j, ha,
                                        h.getUuid(),
                                        h.getComponent().getPath(),
                                        h.getInvocations().entrySet().stream()
                                                .filter(e -> e.getValue().equals(j))
                                                .map(Map.Entry::getKey).toList());
                            }
                        });
            }


        });

        return n;
    }
}
