package com.evento.server.web;

import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerProcessDeadQueueResponseMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerSetEventRetryResponseMessage;
import com.evento.server.bus.MessageBus;
import com.evento.server.bus.NodeAddress;
import com.evento.server.service.discovery.ConsumerService;
import com.evento.server.web.dto.ConsumerDTO;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController()
@RequestMapping("api/consumer")
public class ConsumerController {

    private final ConsumerService consumerService;
    private final MessageBus messageBus;

    public ConsumerController(ConsumerService consumerService, MessageBus messageBus) {
        this.consumerService = consumerService;
        this.messageBus = messageBus;
    }

    @GetMapping("/")
    @Secured("ROLE_WEB")
    public List<ConsumerDTO> findAllConsumers(){
        var consumers = consumerService.findAll();
        var map = new HashMap<String, ConsumerDTO>();
        for(var c : consumers){
            if(!map.containsKey(c.getComponent().getComponentName())){
                var consumer = new ConsumerDTO();
                consumer.setConsumerId(c.getConsumerId());
                consumer.setBundleId(c.getComponent().getBundle().getId());
                consumer.setInstances(new HashSet<>());
                consumer.setBundleVersion(c.getComponent().getBundle().getVersion());
                consumer.setComponentName(c.getComponent().getComponentName());
                consumer.setComponentType(c.getComponent().getComponentType());
                consumer.setContext((c.getConsumerId().split("_"))[3]);
                consumer.setComponentVersion((c.getConsumerId().split("_"))[2]);
                map.put(c.getComponent().getComponentName(), consumer);
            }
            if(messageBus.getCurrentAvailableView().stream().anyMatch(e -> e.instanceId().equals(c.getInstanceId()))){
                map.get(c.getComponent().getComponentName()).getInstances().add(c.getInstanceId());
            }else{
                consumerService.clearInstance(c.getInstanceId());
            }
        }
        return map.values().stream().toList();
    }

    @GetMapping("/{consumerId}")
    @Secured("ROLE_WEB")
    public CompletableFuture<ConsumerFetchStatusResponseMessage>
    getConsumersByConsumerId(@PathVariable String consumerId) throws Exception {
      return consumerService.getConsumerStatusFromNodes(consumerId, messageBus);
    }

    @PutMapping("/{consumerId}/retry/{eventSequenceNumber}")
    @Secured("ROLE_WEB")
    public CompletableFuture<ConsumerSetEventRetryResponseMessage>
    setRetryForConsumerEvent(@PathVariable String consumerId,
                             @PathVariable long eventSequenceNumber,
                             @RequestParam boolean retry) throws Exception {
      return consumerService.setRetryForConsumerEvent(consumerId, eventSequenceNumber, retry, messageBus);
    }

    @PostMapping("/{consumerId}/consume-dead-queue")
    @Secured("ROLE_WEB")
    public CompletableFuture<ConsumerProcessDeadQueueResponseMessage>
    consumeDeadQueue(@PathVariable String consumerId) throws Exception {
      return consumerService.consumeDeadQueue(consumerId, messageBus);
    }
}
