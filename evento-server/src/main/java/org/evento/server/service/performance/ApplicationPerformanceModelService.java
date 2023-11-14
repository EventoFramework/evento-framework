package org.evento.server.service.performance;

import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.server.domain.model.core.Handler;
import org.evento.server.domain.model.core.Payload;
import org.evento.server.domain.repository.core.HandlerRepository;
import org.evento.server.domain.repository.core.PayloadRepository;
import org.evento.server.performance.model.Node;
import org.evento.server.performance.model.PerformanceModel;
import org.evento.server.performance.model.ServiceStation;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.evento.common.performance.PerformanceService.SERVER;


@Service
public class ApplicationPerformanceModelService {

    private final HandlerRepository handlerRepository;

    private final PerformanceStoreService performanceStoreService;
    private final PayloadRepository payloadRepository;

    public ApplicationPerformanceModelService(
            HandlerRepository handlerRepository, PerformanceStoreService performanceStoreService,
            PayloadRepository payloadRepository) {
        this.handlerRepository = handlerRepository;
        this.performanceStoreService = performanceStoreService;
        this.payloadRepository = payloadRepository;
    }

    public PerformanceModel toPerformanceModel() {


        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
            var source = n.source(i);

            var s = n.station(i, false, null);
            s.setPath(i.getComponent().getPath());
            s.setLines(i.getLine() == null ? List.of() : List.of(i.getLine()));

            source.addTarget(s, performanceStoreService);


            for (var p : new HashSet<>(i.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, p, s, null,
                        i.getUuid(),
                        i.getComponent().getPath(),
                        i.getInvocations().entrySet().stream()
                                .filter(e -> e.getValue().equals(p))
                                .map(Map.Entry::getKey).toList());
            }

            //

        });

        return n;

    }

    private void generateInvocationPerformanceModel(PerformanceModel n, List<Handler> handlers, Payload p, ServiceStation source, Node dest,
                                                    String handlerId, String componentPath, List<Integer> lines) {
        if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", "Gateway", p.getName(), p.getType().toString(), false, null, "Gateway_"+handlerId+"_"+p.getName(),
                    componentPath, lines);

            source.addTarget(serverRequestAgent, performanceStoreService);
            // Server -> Component
            var handler = p.getHandlers().get(0);
            var a = n.station(handler, false, null);
            for (var pp : new HashSet<>(handler.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, pp, a, null,
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
                    handler.getReturnType() == null ? null : List.of(handler.getReturnType().getLine()));
            a.addTarget(serverResponseAgent, performanceStoreService);
            if (handler.getReturnType() != null) {
                // Server -> ES
                var esAgent = n.station("event-store", "EventStore", "EventStore", handler.getReturnType().getName(), handler.getReturnType().getType().toString(), false, null, "EventStore_" + handler.getReturnType().getName(),
                        null, null);
                serverResponseAgent.addTarget(esAgent, performanceStoreService);

                if (dest != null)
                    esAgent.addTarget(dest, performanceStoreService);
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
                            esAgent.addTarget(ha, performanceStoreService);
                            for (var i : new HashSet<>(h.getInvocations().values())) {
                                generateInvocationPerformanceModel(n, handlers, i, ha, null,
                                        h.getUuid(),
                                        h.getComponent().getPath(),
                                        h.getInvocations().entrySet().stream()
                                                .filter(e -> e.getValue().equals(i))
                                                .map(Map.Entry::getKey).toList());
                            }
                        });
            } else {
                if (dest != null)
                    serverResponseAgent.addTarget(dest, performanceStoreService);
            }

        } else if (p.getType() == PayloadType.Query) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", "Gateway", p.getName(), p.getType().toString(),
                    false, null, "Gateway_" + p.getName(), componentPath, lines);
            source.addTarget(serverRequestAgent, performanceStoreService);
            // Server -> Component
            var handler = p.getHandlers().get(0);
            var a = n.station(handler, false, null);
            for (var pp : new HashSet<>(handler.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, pp, a, null,
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

            // Server -> Invoker
            if (dest != null)
                serverResponseAgent.addTarget(dest, performanceStoreService);

        }
    }

    public PerformanceModel toPerformanceModel(String handlerId) {

        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getUuid().equals(handlerId)).forEach(i -> {

            var source = n.source(i);

            var s = n.station(i, false, null);

            source.addTarget(s, performanceStoreService);


            for (var p : new HashSet<>(i.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, p, s, null,
                        i.getUuid(),
                        i.getComponent().getPath(),
                        i.getInvocations().entrySet().stream()
                                .filter(e -> e.getValue().equals(p))
                                .map(Map.Entry::getKey).toList());
            }


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
                                generateInvocationPerformanceModel(n, handlers, j, ha, null,
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

    public PerformanceModel toPerformanceModelFromPayload(String payload) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();
        var p = payloadRepository.findById(payload).orElseThrow();
        var source = n.source(p.getName(), p.getType().toString());
		source.setPath(p.getPath());
		if(p.getLine() != null)
			source.setLines(List.of(p.getLine()));
        handlers.stream().filter(h -> h.getHandledPayload().getName().equals(payload) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {

            var s = n.station(i, false, null);

            source.addTarget(s, performanceStoreService);


            for (var pp : new HashSet<>(i.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, pp, s, null,
                        i.getUuid(),
                        i.getComponent().getPath(),
                        i.getInvocations().entrySet().stream()
                                .filter(e -> e.getValue().equals(pp))
                                .map(Map.Entry::getKey).toList());
            }


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
                                generateInvocationPerformanceModel(n, handlers, j, ha, null,
                                        h.getUuid(),
                                        h.getComponent().getPath(),
                                        h.getInvocations().entrySet().stream()
                                                .filter(e -> e.getValue().equals(j))
                                                .map(Map.Entry::getKey).toList());
                            }
                        });
            }


        });


        /*

		handlers.stream().filter(h -> h.getHandledPayload().getName().equals(payload))
				.forEach(hh -> {
					var source = n.source(hh);

				});
*/


        return n;
    }

    public PerformanceModel toPerformanceModelFromComponent(String component) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getComponent().getComponentName().equals(component) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {

            var source = n.source(i);

            var s = n.station(i, false, null);

            source.addTarget(s, performanceStoreService);


            for (var p : new HashSet<>(i.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, p, s, null,
                        i.getUuid(),
                        i.getComponent().getPath(),
                        i.getInvocations().entrySet().stream()
                                .filter(e -> e.getValue().equals(p))
                                .map(Map.Entry::getKey).toList());
            }


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
                                generateInvocationPerformanceModel(n, handlers, j, ha, null,
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

    public PerformanceModel toPerformanceModelFromBundle(String bundle) {
        var n = new PerformanceModel(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getComponent().getBundle().getId().equals(bundle) && h.getHandlerType() != HandlerType.EventSourcingHandler).forEach(i -> {

            var source = n.source(i);

            var s = n.station(i, false, null);

            source.addTarget(s, performanceStoreService);


            for (var p : new HashSet<>(i.getInvocations().values())) {
                generateInvocationPerformanceModel(n, handlers, p, s, null,
                        i.getUuid(),
                        i.getComponent().getPath(),
                        i.getInvocations().entrySet().stream()
                                .filter(e -> e.getValue().equals(p))
                                .map(Map.Entry::getKey).toList());
            }


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
                                generateInvocationPerformanceModel(n, handlers, j, ha, null,
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
