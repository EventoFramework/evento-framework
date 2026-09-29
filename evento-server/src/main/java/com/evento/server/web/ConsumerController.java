package com.evento.server.web;

import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerResponseMessage;
import com.evento.server.bus.BusFacade;
import com.evento.server.service.discovery.ConsumerService;
import com.evento.server.web.dto.ConsumerDTO;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController()
@RequestMapping("api/consumer")
public class ConsumerController {

    private final ConsumerService consumerService;
    private final BusFacade busFacade;

    public ConsumerController(ConsumerService consumerService, BusFacade busFacade) {
        this.consumerService = consumerService;
        this.busFacade = busFacade;
    }

    @GetMapping("/")
    @Secured("ROLE_WEB")
    public List<ConsumerDTO> findAllConsumers(){
        var consumers = consumerService.findAll();
        // Pure read: rows for departed instances are cleaned up on NodeLeft
        // (AutoDiscoveryService), NOT here — deleting from a GET meant that
        // listing consumers while a bundle was reconnecting (e.g. right after
        // a server restart) permanently wiped its registration.
        var availableInstances = busFacade.currentAvailableView().stream()
                .map(e -> e.instanceId())
                .collect(java.util.stream.Collectors.toSet());
        // One entry per consumer id, not per component: a component consuming several
        // contexts (one lane each, e.g. a projector per region) has one consumer id per
        // context, and keying by component showed only whichever context came first.
        var map = new LinkedHashMap<String, ConsumerDTO>();
        for(var c : consumers){
            if(!map.containsKey(c.getConsumerId())){
                var consumer = new ConsumerDTO();
                consumer.setConsumerId(c.getConsumerId());
                consumer.setBundleId(c.getComponent().getBundle().getId());
                consumer.setInstances(new HashSet<>());
                consumer.setBundleVersion(c.getComponent().getBundle().getVersion());
                consumer.setComponentId(c.getComponent().getId());
                consumer.setComponentName(c.getComponent().getComponentName());
                consumer.setComponentType(c.getComponent().getComponentType());
                ConsumerIds.parse(c.getConsumerId(), c.getComponent().getBundle().getId(),
                        c.getComponent().getComponentName()).ifPresent(parts -> {
                    consumer.setContext(parts.context());
                    consumer.setComponentVersion(parts.version());
                });
                map.put(c.getConsumerId(), consumer);
            }
            if(availableInstances.contains(c.getInstanceId())){
                map.get(c.getConsumerId()).getInstances().add(c.getInstanceId());
            }
        }
        return map.values().stream().toList();
    }

    @GetMapping("/{consumerId}")
    @Secured("ROLE_WEB")
    public CompletableFuture<ConsumerFetchStatusResponseMessage>
    getConsumersByConsumerId(@PathVariable String consumerId) throws Exception {
      return consumerService.getConsumerStatusFromNodes(consumerId, busFacade);
    }

    @PutMapping("/{consumerId}/event/{eventSequenceNumber}")
    @Secured("ROLE_ADMIN")
    public CompletableFuture<ConsumerResponseMessage>
    setRetryForConsumerEvent(@PathVariable String consumerId,
                             @PathVariable long eventSequenceNumber,
                             @RequestParam boolean retry) throws Exception {
      return consumerService.setRetryForConsumerEvent(consumerId, eventSequenceNumber, retry, busFacade);
    }

    @DeleteMapping("/{consumerId}/event/{eventSequenceNumber}")
    @Secured("ROLE_ADMIN")
    public CompletableFuture<ConsumerResponseMessage>
    deleteDeadEventFromConsumer(@PathVariable String consumerId,
                             @PathVariable long eventSequenceNumber) throws Exception {
      return consumerService.deleteDeadEventFromEventConsumer(consumerId, eventSequenceNumber, busFacade);
    }

    @PostMapping("/{consumerId}/consume-dead-queue")
    @Secured("ROLE_ADMIN")
    public CompletableFuture<ConsumerResponseMessage>
    consumeDeadQueue(@PathVariable String consumerId) throws Exception {
      return consumerService.consumeDeadQueue(consumerId, busFacade);
    }
}
