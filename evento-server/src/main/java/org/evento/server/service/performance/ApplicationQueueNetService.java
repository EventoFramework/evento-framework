package org.evento.server.service.performance;

import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.server.domain.model.Bundle;
import org.evento.server.domain.model.Handler;
import org.evento.server.domain.model.Payload;
import org.evento.server.domain.performance.modeling.Network;
import org.evento.server.domain.performance.modeling.Post;
import org.evento.server.domain.performance.modeling.Transition;
import org.evento.server.domain.performance.queue.QueueNetwork;
import org.evento.server.domain.performance.queue.ServiceStation;
import org.evento.server.domain.performance.queue.Sink;
import org.evento.server.domain.repository.BundleRepository;
import org.evento.server.domain.repository.HandlerRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.evento.common.performance.PerformanceService.EVENT_STORE;
import static org.evento.common.performance.PerformanceService.SERVER;


@Service
public class ApplicationQueueNetService {

	private final BundleRepository bundleRepository;

	private final HandlerRepository handlerRepository;

	private final PerformanceStoreService performanceStoreService;

	public ApplicationQueueNetService(
			BundleRepository bundleRepository,
			HandlerRepository handlerRepository, PerformanceStoreService performanceStoreService) {
		this.bundleRepository = bundleRepository;
		this.handlerRepository = handlerRepository;
		this.performanceStoreService = performanceStoreService;
	}
	
	public QueueNetwork toQueueNetwork() {


		var n = new QueueNetwork(performanceStoreService::getMeanServiceTime);
		var handlers = handlerRepository.findAll();

		handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
			var s = n.ss(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName(), false, null);
			n.addSource(s);
			for (var p : i.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList())
			{
				var iq = n.ss(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName() + " ["+p.getKey()+"]", false, null);
				generateInvocationQueueNet(n, handlers, p.getValue(), s, iq);
				s = iq;
			}
			s.getTarget().add(n.sink());

		});
		
		return n;
		
	}

	private void generateInvocationQueueNet(QueueNetwork n, List<Handler> handlers, Payload p, ServiceStation source, ServiceStation dest) {
		if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand)
		{
			// Invoker -> Server
			var serverRequestAgent = n.ss(SERVER,"Gateway", p.getName(), false, null);
			source.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var a = n.ss(handler.getBundle().getId(), handler.getComponentName(), handler.getHandledPayload().getName(), false, null);
			serverRequestAgent.getTarget().add(a);
			// Component -> Server
			var serverResponseAgent = n.ss(SERVER, "Gateway",   handler.getReturnType() == null ? "Void" :handler.getReturnType().getName(), false, null);
			a.getTarget().add(serverResponseAgent);
			if (handler.getReturnType() != null)
			{
				// Server -> ES
				var esAgent = n.ss("event-store","EventStore", handler.getReturnType().getName(), false, 1);
				serverResponseAgent.getTarget().add(esAgent);

				// ES -> Invoker
				esAgent.getTarget().add(dest);
				handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
						.filter(h -> h.getHandledPayload().equals(handler.getReturnType())).forEach(h -> {
							// ES -> EventHandler
							var ha = n.ss( h.getBundle().getId(),  h.getComponentName(), h.getHandledPayload().getName(), true, 1);
							esAgent.getTarget().add(ha);
							for (var i : h.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList())
							{
								var iq = n.ss(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName() + " ["+i.getKey()+"]", false, null);
								generateInvocationQueueNet(n, handlers, i.getValue(), ha, iq);
								ha = iq;
							}
							ha.getTarget().add(n.sink());
						});
			}else
			{
				serverResponseAgent.getTarget().add(dest);
			}

		}
		else if (p.getType() == PayloadType.Query)
		{
			// Invoker -> Server
			var serverRequestAgent = n.ss(SERVER,"Gateway",p.getName(), false, null);
			source.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var a = n.ss(handler.getBundle().getId() , handler.getComponentName(), handler.getHandledPayload().getName(), false, null);
			serverRequestAgent.getTarget().add(a);
			// Component -> Server
			var serverResponseAgent = n.ss(SERVER,"Gateway",handler.getReturnType().getName(), false, null);
			a.getTarget().add(serverResponseAgent);

			// Server -> Invoker
			serverResponseAgent.getTarget().add(dest);

		}
	}

	public QueueNetwork toQueueNetwork(String handlerId) {

		var n = new QueueNetwork(performanceStoreService::getMeanServiceTime);
		var handlers = handlerRepository.findAll();

		handlers.stream().filter(h -> h.getUuid().equals(handlerId)).forEach(i -> {
			var s = n.ss(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName(), false, null);

			for (var p : i.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList())
			{
				var iq = n.ss(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName() + " ["+p.getKey()+"]", false, null);
				generateInvocationQueueNet(n, handlers, p.getValue(), s, iq);
				s = iq;
			}


			if (i.getReturnType() != null)
			{

				// Server -> ES
				var esAgent = n.ss("event-store","EventStore", i.getReturnType().getName(), false, 1);
				s.getTarget().add(esAgent);

				// ES -> Invoker
				handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
						.filter(h -> h.getHandledPayload().equals(i.getReturnType())).forEach(h -> {
							// ES -> EventHandler
							var ha = n.ss( h.getBundle().getId(),  h.getComponentName(), h.getHandledPayload().getName(), true, 1);
							esAgent.getTarget().add(ha);
							for (var j : h.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList())
							{
								var iq = n.ss(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName() + " ["+j.getKey()+"]", false, null);
								generateInvocationQueueNet(n, handlers, j.getValue(), ha, iq);
								ha = iq;
							}
							ha.getTarget().add(n.sink());
						});
			} else
			{
				s.getTarget().add(n.sink());
			}



		});

		return n;
	}
}
