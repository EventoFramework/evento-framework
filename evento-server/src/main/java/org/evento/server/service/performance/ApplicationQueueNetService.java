package org.evento.server.service.performance;

import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.server.domain.model.Handler;
import org.evento.server.domain.model.Payload;
import org.evento.server.domain.performance.queue.Node;
import org.evento.server.domain.performance.queue.QueueNetwork;
import org.evento.server.domain.performance.queue.ServiceStation;
import org.evento.server.domain.repository.BundleRepository;
import org.evento.server.domain.repository.HandlerRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.evento.common.performance.PerformanceService.SERVER;


@Service
public class ApplicationQueueNetService {

    private final HandlerRepository handlerRepository;

    private final PerformanceStoreService performanceStoreService;

    public ApplicationQueueNetService(
            HandlerRepository handlerRepository, PerformanceStoreService performanceStoreService) {
        this.handlerRepository = handlerRepository;
        this.performanceStoreService = performanceStoreService;
    }

    public QueueNetwork toQueueNetwork() {


        var n = new QueueNetwork(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
            var source = n.source(i.getHandledPayload().getName());

            var s = n.station(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName(), false, null);

            source.getTarget().add(s);



            for (var p : i.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList()) {
                generateInvocationQueueNet(n, handlers, p.getValue(), s, null);
            }

            //

        });

        return n;

    }

    private void generateInvocationQueueNet(QueueNetwork n, List<Handler> handlers, Payload p, ServiceStation source, Node dest) {
        if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", p.getName(), false, null);
            source.getTarget().add(serverRequestAgent);
            // Server -> Component
            var handler = p.getHandlers().get(0);
            var a = n.station(handler.getBundle().getId(), handler.getComponentName(), handler.getHandledPayload().getName(), false, null);
            serverRequestAgent.getTarget().add(a);
            // Component -> Server
            var serverResponseAgent = n.station(SERVER, "Gateway", handler.getReturnType() == null ? "Void" : handler.getReturnType().getName(), false, null);
            a.getTarget().add(serverResponseAgent);
            if (handler.getReturnType() != null) {
                // Server -> ES
                var esAgent = n.station("event-store", "EventStore", handler.getReturnType().getName(), false, null);
                serverResponseAgent.getTarget().add(esAgent);

                if(dest != null)
                    esAgent.getTarget().add(dest);
                handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                        .filter(h -> h.getHandledPayload().equals(handler.getReturnType())).forEach(h -> {
                    // ES -> EventHandler
                    var sink = n.sink();
                    var perf = performanceStoreService.getMeanServiceTime(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName());
                    var sum = 0.0;
                    for (var i : h.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList()) {
                        var ih = i.getValue().getHandlers().get(0);
                        var st = performanceStoreService.getMeanServiceTime(ih.getBundle().getId(), ih.getComponentName(), ih.getHandledPayload().getName());
                        if (st != null)
                            sum += st;
                    }
                    perf = perf == null ? null : Math.max(perf, sum);
                    var ha = n.station(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName(), true, 1, perf);
                    esAgent.getTarget().add(ha);
                    for (var i : h.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList()) {
                        generateInvocationQueueNet(n, handlers, i.getValue(), ha, sink);
                    }
                    if (h.getInvocations().isEmpty()) {
                        ha.getTarget().add(sink);
                    }
                });
            } else {
                if(dest != null)
                    serverResponseAgent.getTarget().add(dest);
            }

        } else if (p.getType() == PayloadType.Query) {
            // Invoker -> Server
            var serverRequestAgent = n.station(SERVER, "Gateway", p.getName(), false, null);
            source.getTarget().add(serverRequestAgent);
            // Server -> Component
            var handler = p.getHandlers().get(0);
            var a = n.station(handler.getBundle().getId(), handler.getComponentName(), handler.getHandledPayload().getName(), false, null);
            serverRequestAgent.getTarget().add(a);
            // Component -> Server
            var serverResponseAgent = n.station(SERVER, "Gateway", handler.getReturnType().getName(), false, null);
            a.getTarget().add(serverResponseAgent);

            // Server -> Invoker
            if(dest != null)
                serverResponseAgent.getTarget().add(dest);

        }
    }

    public QueueNetwork toQueueNetwork(String handlerId) {

        var n = new QueueNetwork(performanceStoreService::getMeanServiceTime);
        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getUuid().equals(handlerId)).forEach(i -> {

            var source = n.source(i.getHandledPayload().getName());

            var s = n.station(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName(), false, null);

            source.getTarget().add(s);


            for (var p : i.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList()) {
                generateInvocationQueueNet(n, handlers, p.getValue(), s, null);
            }



            if (i.getReturnType() != null) {

                // Server -> ES
                var esAgent = n.station("event-store", "EventStore", i.getReturnType().getName(), false, 1);
                s.getTarget().add(esAgent);

                // ES -> Invoker
                handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                        .filter(h -> h.getHandledPayload().equals(i.getReturnType())).forEach(h -> {
                            // ES -> EventHandler
                            var ha = n.station(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName(), true, 1);
                            esAgent.getTarget().add(ha);

                            for (var j : h.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList()) {
                                //var iq = n.station(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName() + " [" + j.getKey() + "]", false, null);
                                generateInvocationQueueNet(n, handlers, j.getValue(), ha, null);
                            }
                        });
            }


        });

        return n;
    }
}
