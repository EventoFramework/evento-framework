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

		HashMap<String, Post> instancesPosts = new HashMap<>();
		var n = new Network();
		
		for (Bundle bundle : bundleRepository.findAll())
		{
			var name = "instances:bundle:" + bundle.getName();
			//instancesPosts.put(name, n.post(name, 1));
		}
		//instancesPosts.put("instances:server", n.post("instances:server", 1));
		//instancesPosts.put("instances:es", n.post("instances:server", 1));

		var handlers = handlerRepository.findAll();

		handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
			var sourceQueue = n.post("", 1);
			var sourceAgent = n.transition(i.getBundle().getName() + ":" + i.getHandledPayload().getName());
			sourceQueue.getTarget().add(sourceAgent);
			sourceAgent.getTarget().add(sourceQueue);

			n.addSource(sourceQueue);


			for (Payload p : i.getInvocations())
			{
				var iq = n.post("");
				generateInvocationPetriNet(n, handlers, p, sourceAgent, iq);
				sourceAgent = n.transition(i.getBundle().getName() + ":response:" + i.getHandledPayload().getName());
				iq.getTarget().add(sourceAgent);
			}

			var sinkQueue = n.post("", 1);
			var sinkAgent = n.transition("sink");
			sinkQueue.getTarget().add(sinkAgent);

			sourceAgent.getTarget().add(sinkQueue);

		});
		
		return n;
		
	}


	private void generateInvocationPetriNet(Network n, List<Handler> handlers, Payload p, Transition sourceAgent, Post sinkQueue) {
		if (p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand)
		{
			// Invoker -> Server
			var serverRequestQueue = n.post("");
			sourceAgent.getTarget().add(serverRequestQueue);
			var serverRequestAgent = n.transition("server:request:" + p.getName());
			serverRequestQueue.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var q = n.post("");
			var a = n.transition(handler.getBundle().getName() + ":" + handler.getComponentName() + ":" + handler.getHandledPayload().getName());
			serverRequestAgent.getTarget().add(q);
			q.getTarget().add(a);
			// Component -> Server
			var serverResponseQueue = n.post("");
			a.getTarget().add(serverResponseQueue);
			var serverResponseAgent = n.transition("server:response:" + handler.getHandledPayload().getName());
			serverResponseQueue.getTarget().add(serverResponseAgent);

			if (handler.getReturnType() != null)
			{
				// Server -> ES
				var esQueue = n.post("");
				serverResponseAgent.getTarget().add(esQueue);
				var esAgent = n.transition("event-store:save:" + handler.getReturnType().getName());
				esQueue.getTarget().add(esAgent);

				// ES -> Invoker
				esAgent.getTarget().add(sinkQueue);
				handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
						.filter(h -> h.getHandledPayload().equals(handler.getReturnType())).forEach(h -> {
							// ES -> EventHandler
							var hq = n.post("");
							var ha = n.transition( h.getBundle().getName() + ":request:" + h.getComponentName() + ":" + h.getHandledPayload().getName());

							esAgent.getTarget().add(hq);
							hq.getTarget().add(ha);
							for (Payload invocation : h.getInvocations())
							{
								var iq = n.post("");
								generateInvocationPetriNet(n, handlers, invocation, ha, iq);
								ha = n.transition(h.getBundle().getName() + ":response:" + h.getComponentName() + ":" + h.getHandledPayload().getName());
								iq.getTarget().add(ha);
							}

							if(h.getHandlerType() == HandlerType.SagaEventHandler){
								var ssq = n.post("");
								var ssa = n.transition( "server:store-saga-state:" + h.getComponentName());
								ha.getTarget().add(ssq);
								ssq.getTarget().add(ssa);
								var ssqOk = n.post("");
								var ssaOk = n.transition( "sink");
								ssa.getTarget().add(ssqOk);
								ssqOk.getTarget().add(ssaOk);
							} else if (h.getHandlerType() == HandlerType.EventHandler)
							{
								var psq = n.post("");
								var psa = n.transition( "server:store-projector-state:" + h.getComponentName());
								ha.getTarget().add(psq);
								psq.getTarget().add(psa);
								var psqOk = n.post("");
								var psaOk = n.transition( "sink");
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
			var serverRequestQueue = n.post("");
			sourceAgent.getTarget().add(serverRequestQueue);
			var serverRequestAgent = n.transition("server:request:" + p.getName());
			serverRequestQueue.getTarget().add(serverRequestAgent);
			// Server -> Component
			var handler = p.getHandlers().get(0);
			var q = n.post("");
			var a = n.transition(handler.getBundle().getName() + ":" + handler.getComponentName() + ":" + handler.getHandledPayload().getName());
			serverRequestAgent.getTarget().add(q);
			q.getTarget().add(a);
			// Component -> Server
			var serverResponseQueue = n.post("");
			a.getTarget().add(serverResponseQueue);
			var serverResponseAgent = n.transition("server:response:" + handler.getReturnType().getName());
			serverResponseQueue.getTarget().add(serverResponseAgent);

			// Server -> Invoker
			serverResponseAgent.getTarget().add(sinkQueue);

		}
	}
}
