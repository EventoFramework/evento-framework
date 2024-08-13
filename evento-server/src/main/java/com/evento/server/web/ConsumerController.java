package com.evento.server.web;

import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.server.bus.MessageBus;
import com.evento.server.bus.NodeAddress;
import com.evento.server.service.discovery.ConsumerService;
import com.evento.server.web.dto.ConsumerDTO;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                consumer.setContext(Arrays.stream(c.getConsumerId().split("_")).toList().getLast());
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
}
