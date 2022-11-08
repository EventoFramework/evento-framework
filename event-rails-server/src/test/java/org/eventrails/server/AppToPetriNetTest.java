package org.eventrails.server;

import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Payload;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.model.types.PayloadType;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.eventrails.server.performance.Post;
import org.eventrails.server.performance.Transition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
@ActiveProfiles("local")
public class AppToPetriNetTest {

    @Autowired
    BundleRepository bundleRepository;

    @Autowired
    HandlerRepository handlerRepository;

    private HashMap<String, Post> instancesPosts = new HashMap<>();
    private AtomicLong id = new AtomicLong();

    private ArrayList<Post> paths = new ArrayList<>();

    @Test
    public void test() {


        for (Bundle bundle : bundleRepository.findAll()) {
            var name = "instances:bundle:" + bundle.getName();
            instancesPosts.put(name, new Post(id.getAndIncrement(), name, 1));
        }
        instancesPosts.put("instances:server", new Post(id.getAndIncrement(), "instances:server", 1));
        instancesPosts.put("instances:es", new Post(id.getAndIncrement(), "instances:server", 1));

        var handlers = handlerRepository.findAll();

        handlers.stream().filter(h -> h.getHandlerType() == HandlerType.InvocationHandler).forEach(i -> {
            var sourceQueue = new Post(id.getAndIncrement(), "queue:source:" + i.getBundle().getName() + ":" + i.getHandledPayload().getName(), 1);
            paths.add(sourceQueue);
            var sourceAgent = new Transition(id.getAndIncrement(), "agent:source:" + i.getBundle().getName() + ":" + i.getHandledPayload().getName());
            sourceQueue.getTarget().add(sourceAgent);
            sourceAgent.getTarget().add(sourceQueue);


            var sinkQueue = new Post(id.getAndIncrement(), "queue:sink:" + i.getBundle().getName() + ":" + i.getHandledPayload().getName(), 1);
            var sinkAgent = new Transition(id.getAndIncrement(), "agent:sink:" + i.getBundle().getName() + ":" + i.getHandledPayload().getName());
            sinkQueue.getTarget().add(sinkAgent);

            for (Payload p : i.getInvocations()) {
                if(p.getType() == PayloadType.Command || p.getType() == PayloadType.DomainCommand || p.getType() == PayloadType.ServiceCommand) {
                    // Invoker -> Server
                    var serverRequestQueue = new Post(id.getAndIncrement(), "queue:server:" + p.getName() + ":request");
                    sourceAgent.getTarget().add(serverRequestQueue);
                    var serverRequestAgent = new Transition(id.getAndIncrement(), "agent:server:" + p.getName() + ":request");
                    serverRequestQueue.getTarget().add(serverRequestAgent);
                    // Server -> Component
                    var handler = p.getHandlers().get(0);
                    var q = new Post(id.getAndIncrement(), "queue:" + handler.getBundle().getName() + ":" + handler.getComponentName() + ":" + handler.getHandledPayload().getName());
                    var a = new Transition(id.getAndIncrement(), "agent:" + handler.getBundle().getName() + ":" + handler.getComponentName() + ":" + handler.getHandledPayload().getName());
                    serverRequestAgent.getTarget().add(q);
                    q.getTarget().add(a);
                    // Component -> Server
                    var serverResponseQueue = new Post(id.getAndIncrement(), "queue:server:" + p.getName() + ":response");
                    a.getTarget().add(serverResponseQueue);
                    var serverResponseAgent = new Transition(id.getAndIncrement(), "agent:server:" + p.getName() + ":response");
                    serverResponseQueue.getTarget().add(serverResponseAgent);

                    if(handler.getReturnType() !=null) {
                        // Server -> ES
                        var esQueue = new Post(id.getAndIncrement(), "queue:es:" + handler.getReturnType().getName() + ":request");
                        serverResponseAgent.getTarget().add(esQueue);
                        var esAgent = new Transition(id.getAndIncrement(), "agent:es:" + handler.getReturnType().getName() + ":request");
                        esQueue.getTarget().add(esAgent);

                        // ES -> Invoker
                        esAgent.getTarget().add(sinkQueue);
                        handlers.stream().filter(h -> h.getHandlerType() != HandlerType.EventSourcingHandler)
                                .filter(h -> h.getHandledPayload().equals(handler.getReturnType())).forEach(h -> {
                            // ES -> EventHandler
                            var hq = new Post(id.getAndIncrement(), "queue:" + handler.getBundle().getName() + ":" + handler.getComponentName() + ":" + handler.getHandledPayload().getName());
                            var ha = new Transition(id.getAndIncrement(), "agent:" + handler.getBundle().getName() + ":" + handler.getComponentName() + ":" + handler.getHandledPayload().getName());

                            esAgent.getTarget().add(hq);
                            hq.getTarget().add(ha);
                            ha.getTarget().add(sinkQueue);

                        });
                    }else{
                        serverResponseAgent.getTarget().add(sinkQueue);
                    }
                }



            }


        });

        System.out.println("bye");


    }
}
