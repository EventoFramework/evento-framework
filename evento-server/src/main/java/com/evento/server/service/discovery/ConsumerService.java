package com.evento.server.service.discovery;

import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.consumer.*;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.server.bus.MessageBus;
import com.evento.server.domain.model.core.Consumer;
import com.evento.server.domain.repository.core.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * This class represents a service for managing consumers in the system.
 *
 * The class provides methods for registering consumers and saving them to the database.
 * Consumers are components that consume resources or services provided by other components.
 *
 * The class requires three dependencies:
 *   - LockRegistry: A lock registry used for obtaining locks for consumer discovery.
 *   - ComponentRepository: A repository for accessing and managing components in the database.
 *   - ConsumerRepository: A repository for accessing and managing consumers in the database.
 *
 * The class is annotated with the @Service annotation to indicate that it is a service component.
 * It also makes use of the logging framework by defining a static logger instance.
 */
@Service
public class ConsumerService {

    private static final Logger logger = LogManager.getLogger(ConsumerService.class);
    private final LockRegistry lockRegistry;
    private final ComponentRepository componentRepository;
    private final ConsumerRepository consumerRepository;
    private final String eventoServerInstanceId;

    public ConsumerService(LockRegistry lockRegistry, ComponentRepository componentRepository,
                           ConsumerRepository consumerRepository,
                           @Value("${evento.server.instance.id}") String eventoServerInstanceId) {
        this.lockRegistry = lockRegistry;
        this.componentRepository = componentRepository;
        this.consumerRepository = consumerRepository;
        this.eventoServerInstanceId = eventoServerInstanceId;
    }

    /**
     * Registers consumers for a bundle in the system.
     * This method takes in the source bundle ID, source instance ID, source bundle version,
     * and bundle consumer registration message, and registers the consumers in the system.
     * The registration process involves creating Consumer objects for projector consumers,
     * saga consumers, and observer consumers, and saving them in the consumer repository.
     *
     * @param sourceBundleId       the ID of the source bundle
     * @param sourceInstanceId     the ID of the source instance
     * @param sourceBundleVersion  the version of the source bundle
     * @param cr                   the bundle consumer registration message containing consumer information
     */
    public void registerConsumers(String sourceBundleId,
                                  String sourceInstanceId,
                                  long sourceBundleVersion,
                                  BundleConsumerRegistrationMessage cr) {
        try {
            var lock = lockRegistry.obtain("CONSUMER_DISCOVERY:" + sourceInstanceId);
            if (!lock.tryLock())
                return;
            try {
                logger.info("Discovering consumers in bundle: %s and instance: %s".formatted(sourceBundleId, sourceInstanceId));
                var consumers = new ArrayList<Consumer>();
                cr.getProjectorConsumers().forEach((k,v) -> {
                    for (String id : v) {
                        var c = new Consumer();
                        c.setInstanceId(sourceInstanceId);
                        c.setConsumerId(id);
                        c.setComponent(componentRepository.getReferenceById(k));
                        c.setIdentifier(c.getInstanceId() + "_" + c.getConsumerId());
                        consumers.add(c);
                    }
                });
                cr.getSagaConsumers().forEach((k,v) -> {
                    for (String id : v) {
                        var c = new Consumer();
                        c.setInstanceId(sourceInstanceId);
                        c.setConsumerId(id);
                        c.setComponent(componentRepository.getReferenceById(k));
                        c.setIdentifier(c.getInstanceId() + "_" + c.getConsumerId());
                        consumers.add(c);
                    }
                });
                cr.getObserverConsumers().forEach((k,v) -> {
                    for (String id : v) {
                        var c = new Consumer();
                        c.setInstanceId(sourceInstanceId);
                        c.setConsumerId(id);
                        c.setComponent(componentRepository.getReferenceById(k));
                        c.setIdentifier(c.getInstanceId() + "_" + c.getConsumerId());
                        consumers.add(c);
                    }
                });
                consumerRepository.saveAll(consumers);
            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public List<Consumer> findAll() {
        return consumerRepository.findAll();
    }

    public CompletableFuture<ConsumerFetchStatusResponseMessage> getConsumerStatusFromNodes(String consumerId, MessageBus messageBus) throws Exception {
        var consumers =  consumerRepository.findAllByConsumerId(consumerId);
        var instances = consumers.stream()
                .map(Consumer::getInstanceId)
                .collect(Collectors.toSet());
        var address =  messageBus.getCurrentView().stream().filter(n ->
                instances.contains(n.instanceId())).findFirst()
                .orElseThrow();
        var request  = new EventoRequest();
        request.setCorrelationId(UUID.randomUUID().toString());
        request.setTimestamp(System.currentTimeMillis());
        request.setSourceBundleId("evento-server");
        request.setSourceInstanceId(eventoServerInstanceId);
        request.setSourceBundleVersion(0);
        request.setBody(new ConsumerFetchStatusRequestMessage(consumerId, consumers.getFirst().getComponent().getComponentType()));
        var future = new CompletableFuture<ConsumerFetchStatusResponseMessage>();
        messageBus.forward(request, address, (c) -> {
            if(c.getBody() instanceof ConsumerFetchStatusResponseMessage resp){
                future.complete(resp);
            } else if (c.getBody() instanceof ExceptionWrapper e) {
                future.completeExceptionally(e.toException());
            }else{
                future.completeExceptionally(new RuntimeException("Invalid response from component while fetching for consumer status"));
            }
        });
        return future;

    }

    public void clearInstance(String instanceId) {
        consumerRepository.deleteAllByInstanceId(instanceId);
    }

    public CompletableFuture<ConsumerSetEventRetryResponseMessage> setRetryForConsumerEvent(String consumerId, long eventSequenceNumber, boolean retry, MessageBus messageBus) throws Exception {
        var consumers =  consumerRepository.findAllByConsumerId(consumerId);
        var instances = consumers.stream()
                .map(Consumer::getInstanceId)
                .collect(Collectors.toSet());
        var address =  messageBus.getCurrentView().stream().filter(n ->
                        instances.contains(n.instanceId())).findFirst()
                .orElseThrow();
        var request  = new EventoRequest();
        request.setCorrelationId(UUID.randomUUID().toString());
        request.setTimestamp(System.currentTimeMillis());
        request.setSourceBundleId("evento-server");
        request.setSourceInstanceId(eventoServerInstanceId);
        request.setSourceBundleVersion(0);
        request.setBody(new ConsumerSetEventRetryRequestMessage(consumerId, consumers.getFirst().getComponent().getComponentType(),
                eventSequenceNumber, retry));
        var future = new CompletableFuture<ConsumerSetEventRetryResponseMessage>();
        messageBus.forward(request, address, (c) -> {
            if(c.getBody() instanceof ConsumerSetEventRetryResponseMessage resp){
                future.complete(resp);
            } else if (c.getBody() instanceof ExceptionWrapper e) {
                future.completeExceptionally(e.toException());
            }else{
                future.completeExceptionally(new RuntimeException("Invalid response from component while fetching for consumer status"));
            }
        });
        return future;
    }

    public CompletableFuture<ConsumerProcessDeadQueueResponseMessage> consumeDeadQueue(String consumerId, MessageBus messageBus) throws Exception {
        var consumers =  consumerRepository.findAllByConsumerId(consumerId);
        var instances = consumers.stream()
                .map(Consumer::getInstanceId)
                .collect(Collectors.toSet());
        var address =  messageBus.getCurrentView().stream().filter(n ->
                        instances.contains(n.instanceId())).findFirst()
                .orElseThrow();
        var request  = new EventoRequest();
        request.setCorrelationId(UUID.randomUUID().toString());
        request.setTimestamp(System.currentTimeMillis());
        request.setSourceBundleId("evento-server");
        request.setSourceInstanceId(eventoServerInstanceId);
        request.setSourceBundleVersion(0);
        request.setBody(new ConsumerProcessDeadQueueRequestMessage(consumerId, consumers.getFirst().getComponent().getComponentType()));
        var future = new CompletableFuture<ConsumerProcessDeadQueueResponseMessage>();
        messageBus.forward(request, address, (c) -> {
            if(c.getBody() instanceof ConsumerProcessDeadQueueResponseMessage resp){
                future.complete(resp);
            } else if (c.getBody() instanceof ExceptionWrapper e) {
                future.completeExceptionally(e.toException());
            }else{
                future.completeExceptionally(new RuntimeException("Invalid response from component while fetching for consumer status"));
            }
        });
        return future;
    }
}
