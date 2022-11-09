package org.eventrails.server.service.performance;

import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Payload;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.model.types.PayloadType;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class ApplicationPetriNetService {

	private final BundleRepository bundleRepository;

	private final HandlerRepository handlerRepository;

	public ApplicationPetriNetService(BundleRepository bundleRepository, HandlerRepository handlerRepository) {
		this.bundleRepository = bundleRepository;
		this.handlerRepository = handlerRepository;
	}
	
	public Network toPetriNet() {


		var n = new Network();
		
		for (Bundle bundle : bundleRepository.findAll())
		{
			n.instancePost(bundle.getName());
		}
		n.instancePost("server");
		n.instancePost("event-store");

		var handlers = handlerRepository.findAll();

		handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
			var sourceQueue = n.post(i.getBundle().getName(), i.getComponentName(), i.getHandledPayload().getName(), 1);
			var sourceAgent = n.transition(i.getBundle().getName(), i.getComponentName(), i.getHandledPayload().getName());
			sourceQueue.getTarget().add(sourceAgent);
			sourceAgent.getTarget().add(sourceQueue);

			n.addSource(sourceQueue);


			for (Payload p : i.getInvocations())
			{
				var iq = n.post(i.getBundle().getName(), i.getComponentName(), i.getHandledPayload().getName());
				generateInvocationPetriNet(n, handlers, p, sourceAgent, iq);
				sourceAgent = n.transition(i.getBundle().getName(), i.getComponentName(), i.getHandledPayload().getName());
				iq.getTarget().add(sourceAgent);
			}

			var sinkQueue = n.post(i.getBundle().getName(), i.getComponentName(), "Sink", 1);
			var sinkAgent = n.transition(i.getBundle().getName(), i.getComponentName(), "Sink");
			sinkQueue.getTarget().add(sinkAgent);

			sourceAgent.getTarget().add(sinkQueue);

		});
		
		return n;
		
	}

	private void generateInvocationPetriNet(Network n, List<Handler> handlers, Payload p, Transition sourceAgent, Post sinkQueue) {
		if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand)
		{
			// Invoker -> Server
			var serverRequestQueue = n.post("server","Gateway", p.getName());
			sourceAgent.getTarget().add(serverRequestQueue);
			var serverRequestAgent = n.transition("server","Gateway", p.getName());
			serverRequestQueue.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var q = n.post(handler.getBundle().getName(), handler.getComponentName(), handler.getHandledPayload().getName());
			var a = n.transition(handler.getBundle().getName(), handler.getComponentName(), handler.getHandledPayload().getName());
			serverRequestAgent.getTarget().add(q);
			q.getTarget().add(a);
			// Component -> Server
			var serverResponseQueue = n.post("server", "Gateway",  handler.getHandledPayload().getName());
			a.getTarget().add(serverResponseQueue);
			var serverResponseAgent = n.transition("server", "Gateway",  handler.getHandledPayload().getName());
			serverResponseQueue.getTarget().add(serverResponseAgent);

			if (handler.getReturnType() != null)
			{
				// Server -> ES
				var esQueue = n.post("event-store","EventStore", handler.getReturnType().getName());
				serverResponseAgent.getTarget().add(esQueue);
				var esAgent = n.transition("event-store","EventStore", handler.getReturnType().getName());
				esQueue.getTarget().add(esAgent);

				// ES -> Invoker
				esAgent.getTarget().add(sinkQueue);
				handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
						.filter(h -> h.getHandledPayload().equals(handler.getReturnType())).forEach(h -> {
							// ES -> EventHandler
							var hq = n.post(h.getBundle().getName(),  h.getComponentName(), h.getHandledPayload().getName());
							var ha = n.transition( h.getBundle().getName(),  h.getComponentName(), h.getHandledPayload().getName());

							esAgent.getTarget().add(hq);
							hq.getTarget().add(ha);
							for (Payload invocation : h.getInvocations())
							{
								var iq = n.post(h.getBundle().getName(), h.getComponentName(), h.getHandledPayload().getName());
								generateInvocationPetriNet(n, handlers, invocation, ha, iq);
								ha = n.transition(h.getBundle().getName(), h.getComponentName(), h.getHandledPayload().getName());
								iq.getTarget().add(ha);
							}

							if(h.getHandlerType() == HandlerType.SagaEventHandler){
								var ssq = n.post("server","SagaStore", h.getComponentName());
								var ssa = n.transition( "server","SagaStore", h.getComponentName());
								ha.getTarget().add(ssq);
								ssq.getTarget().add(ssa);
								var ssqOk = n.post("server", "SagaStore", "Sink");
								var ssaOk = n.transition( "server", "SagaStore", "Sink");
								ssa.getTarget().add(ssqOk);
								ssqOk.getTarget().add(ssaOk);
							} else if (h.getHandlerType() == HandlerType.EventHandler)
							{
								var psq = n.post("server","ProjectorStore",h.getComponentName());
								var psa = n.transition( "server","ProjectorStore",h.getComponentName());
								ha.getTarget().add(psq);
								psq.getTarget().add(psa);
								var psqOk = n.post("server","ProjectorStore", "Sink");
								var psaOk = n.transition( "server","ProjectorStore", "Sink");
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
			var serverRequestQueue = n.post("server","Gateway",p.getName());
			sourceAgent.getTarget().add(serverRequestQueue);
			var serverRequestAgent = n.transition("server","Gateway",p.getName());
			serverRequestQueue.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var q = n.post(handler.getBundle().getName() , handler.getComponentName(), handler.getHandledPayload().getName());
			var a = n.transition(handler.getBundle().getName() , handler.getComponentName(), handler.getHandledPayload().getName());
			serverRequestAgent.getTarget().add(q);
			q.getTarget().add(a);
			// Component -> Server
			var serverResponseQueue = n.post("server","Gateway",handler.getReturnType().getName());
			a.getTarget().add(serverResponseQueue);
			var serverResponseAgent = n.transition("server","Gateway",handler.getReturnType().getName());
			serverResponseQueue.getTarget().add(serverResponseAgent);

			// Server -> Invoker
			serverResponseAgent.getTarget().add(sinkQueue);

		}
	}
}
