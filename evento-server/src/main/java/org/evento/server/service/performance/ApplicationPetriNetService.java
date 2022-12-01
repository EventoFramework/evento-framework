package org.evento.server.service.performance;

import org.evento.server.domain.model.Bundle;
import org.evento.server.domain.model.Handler;
import org.evento.server.domain.model.Payload;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.server.domain.performance.modeling.Network;
import org.evento.server.domain.performance.modeling.Post;
import org.evento.server.domain.performance.modeling.Transition;
import org.evento.server.domain.performance.modeling.*;
import org.evento.server.domain.repository.BundleRepository;
import org.evento.server.domain.repository.HandlerRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.evento.common.performance.PerformanceService.EVENT_STORE;
import static org.evento.common.performance.PerformanceService.SERVER;


@Service
public class ApplicationPetriNetService {

	private final BundleRepository bundleRepository;

	private final HandlerRepository handlerRepository;

	private final PerformanceStoreService performanceStoreService;

	public ApplicationPetriNetService(
			BundleRepository bundleRepository,
			HandlerRepository handlerRepository, PerformanceStoreService performanceStoreService) {
		this.bundleRepository = bundleRepository;
		this.handlerRepository = handlerRepository;
		this.performanceStoreService = performanceStoreService;
	}
	
	public Network toPetriNet() {


		var n = new Network(performanceStoreService::getMeanServiceTime);
		
		for (Bundle bundle : bundleRepository.findAll())
		{
			n.instancePost(bundle.getId());
		}
		n.instancePost(SERVER);
		n.instancePost(EVENT_STORE);

		var handlers = handlerRepository.findAll();

		handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
			var sourceQueue = n.post(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName(), 1);
			var sourceAgent = n.transition(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName(), false);
			sourceQueue.getTarget().add(sourceAgent);
			sourceAgent.getTarget().add(sourceQueue);

			n.addSource(sourceQueue);


			for (var p : i.getInvocations().entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).toList())
			{
				var iq = n.post(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName());
				generateInvocationPetriNet(n, handlers, p.getValue(), p.getKey(), sourceAgent, iq);
				sourceAgent = n.transition(i.getBundle().getId(), i.getComponentName(), i.getHandledPayload().getName() + " ["+p.getKey()+"]", false);
				iq.getTarget().add(sourceAgent);
			}

			var sinkQueue = n.post(i.getBundle().getId(), i.getComponentName(), "Sink", 1);
			var sinkAgent = n.transition(i.getBundle().getId(), i.getComponentName(), "Sink", false);
			sinkQueue.getTarget().add(sinkAgent);

			sourceAgent.getTarget().add(sinkQueue);

		});
		
		return n;
		
	}

	private void generateInvocationPetriNet(Network n, List<Handler> handlers, Payload p, int line, Transition sourceAgent, Post sinkQueue) {
		if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand)
		{
			// Invoker -> Server
			var serverRequestQueue = n.post(SERVER,"Gateway", p.getName());
			sourceAgent.getTarget().add(serverRequestQueue);
			var serverRequestAgent = n.transition(SERVER,"Gateway", p.getName(), false);
			serverRequestQueue.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var q = n.post(handler.getBundle().getId(), handler.getComponentName(), handler.getHandledPayload().getName());
			var a = n.transition(handler.getBundle().getId(), handler.getComponentName(), handler.getHandledPayload().getName(), false);
			serverRequestAgent.getTarget().add(q);
			q.getTarget().add(a);
			// Component -> Server
			var serverResponseQueue = n.post(SERVER, "Gateway", handler.getReturnType() == null ? "Void" : handler.getReturnType().getName());
			a.getTarget().add(serverResponseQueue);
			var serverResponseAgent = n.transition(SERVER, "Gateway",   handler.getReturnType() == null ? "Void" :handler.getReturnType().getName(), false);
			serverResponseQueue.getTarget().add(serverResponseAgent);

			if (handler.getReturnType() != null)
			{
				// Server -> ES
				var esQueue = n.post("event-store","EventStore", handler.getReturnType().getName());
				serverResponseAgent.getTarget().add(esQueue);
				var esAgent = n.transition("event-store","EventStore", handler.getReturnType().getName(), false);
				esQueue.getTarget().add(esAgent);

				// ES -> Invoker
				esAgent.getTarget().add(sinkQueue);
				handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
						.filter(h -> h.getHandledPayload().equals(handler.getReturnType())).forEach(h -> {
							// ES -> EventHandler
							var hq = n.post(h.getBundle().getId(),  h.getComponentName(), h.getHandledPayload().getName());
							var ha = n.transition( h.getBundle().getId(),  h.getComponentName(), h.getHandledPayload().getName(), true);

							esAgent.getTarget().add(hq);
							hq.getTarget().add(ha);
							for (var invocation : h.getInvocations().entrySet().stream().sorted((s,b)->b.getKey()-s.getKey()).toList())
							{
								var iq = n.post(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName());
								generateInvocationPetriNet(n, handlers, invocation.getValue(), invocation.getKey(), ha, iq);
								ha = n.transition(h.getBundle().getId(), h.getComponentName(), h.getHandledPayload().getName(), false);
								iq.getTarget().add(ha);
							}

							if(h.getHandlerType() == HandlerType.SagaEventHandler){
								var ssq = n.post(SERVER,"SagaStore", h.getComponentName());
								var ssa = n.transition( SERVER,"SagaStore", h.getComponentName(), false);
								ha.getTarget().add(ssq);
								ssq.getTarget().add(ssa);
								var ssqOk = n.post(SERVER, "SagaStore", "Sink");
								var ssaOk = n.transition( SERVER, "SagaStore", "Sink", false);
								ssa.getTarget().add(ssqOk);
								ssqOk.getTarget().add(ssaOk);
							} else if (h.getHandlerType() == HandlerType.EventHandler)
							{
								var psq = n.post(SERVER,"ProjectorStore",h.getComponentName());
								var psa = n.transition( SERVER,"ProjectorStore",h.getComponentName(), false);
								ha.getTarget().add(psq);
								psq.getTarget().add(psa);
								var psqOk = n.post(SERVER,"ProjectorStore", "Sink");
								var psaOk = n.transition( SERVER,"ProjectorStore", "Sink", false);
								psa.getTarget().add(psqOk);
								psqOk.getTarget().add(psaOk);
							}

						});
			} else
			{
				serverResponseAgent.getTarget().add(sinkQueue);
			}
		}

		if (p.getType() == PayloadType.Query)
		{
			// Invoker -> Server
			var serverRequestQueue = n.post(SERVER,"Gateway",p.getName());
			sourceAgent.getTarget().add(serverRequestQueue);
			var serverRequestAgent = n.transition(SERVER,"Gateway",p.getName(), false);
			serverRequestQueue.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var q = n.post(handler.getBundle().getId() , handler.getComponentName(), handler.getHandledPayload().getName());
			var a = n.transition(handler.getBundle().getId() , handler.getComponentName(), handler.getHandledPayload().getName(), false);
			serverRequestAgent.getTarget().add(q);
			q.getTarget().add(a);
			// Component -> Server
			var serverResponseQueue = n.post(SERVER,"Gateway",handler.getReturnType().getName());
			a.getTarget().add(serverResponseQueue);
			var serverResponseAgent = n.transition(SERVER,"Gateway",handler.getReturnType().getName(), false);
			serverResponseQueue.getTarget().add(serverResponseAgent);

			// Server -> Invoker
			serverResponseAgent.getTarget().add(sinkQueue);

		}
	}
}
