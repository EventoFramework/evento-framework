package com.evento.server.service.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.consumer.*;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.bus.BusFacade;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Consumer;
import com.evento.server.domain.repository.core.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * This class represents a service for managing consumers in the system.
 * <p>
 * The class provides methods for registering consumers and saving them to the database.
 * Consumers are components that consume resources or services provided by other components.
 * <p>
 * The class requires three dependencies:
 *   - LockRegistry: A lock registry used for obtaining locks for consumer discovery.
 *   - ComponentRepository: A repository for accessing and managing components in the database.
 *   - ConsumerRepository: A repository for accessing and managing consumers in the database.
 * <p>
 * The class is annotated with the @Service annotation to indicate that it is a service component.
 * It also makes use of the logging framework by defining a static logger instance.
 */
@Service
public class ConsumerService {

    private static final Logger logger = LogManager.getLogger(ConsumerService.class);
    private final ComponentRepository componentRepository;
    private final ConsumerRepository consumerRepository;
    private final BundleRepository bundleRepository;
    private final String eventoServerInstanceId;

    private final PgDistributedLock distributedLock;

    public ConsumerService(ComponentRepository componentRepository,
                           ConsumerRepository consumerRepository,
                           BundleRepository bundleRepository,
                           @Value("${evento.server.instance.id}") String eventoServerInstanceId,
                           DataSource dataSource) {
        this.componentRepository = componentRepository;
        this.consumerRepository = consumerRepository;
        this.bundleRepository = bundleRepository;
        this.eventoServerInstanceId = eventoServerInstanceId;
        this.distributedLock = new PgDistributedLock(dataSource);
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
            var key = "CONSUMER_DISCOVERY:" + sourceInstanceId;
            this.distributedLock.lockedArea(key, () -> {
                logger.info("Discovering consumers in bundle: %s and instance: %s".formatted(sourceBundleId, sourceInstanceId));
                var consumers = new ArrayList<Consumer>();
                collectConsumers(cr.getProjectorConsumers(), ComponentType.Projector,
                        sourceBundleId, sourceInstanceId, sourceBundleVersion, consumers);
                collectConsumers(cr.getSagaConsumers(), ComponentType.Saga,
                        sourceBundleId, sourceInstanceId, sourceBundleVersion, consumers);
                collectConsumers(cr.getObserverConsumers(), ComponentType.Observer,
                        sourceBundleId, sourceInstanceId, sourceBundleVersion, consumers);
                consumerRepository.saveAll(consumers);
            });
        } catch (Exception e) {
            logger.error("Consumer registration failed for bundle: %s and instance: %s — its consumers stay unregistered until the next reconnect"
                    .formatted(sourceBundleId, sourceInstanceId), e);
        }
    }

    private void collectConsumers(Map<String, ? extends Set<String>> consumersByComponent,
                                  ComponentType componentType,
                                  String sourceBundleId,
                                  String sourceInstanceId,
                                  long sourceBundleVersion,
                                  List<Consumer> out) {
        if (consumersByComponent == null) return;
        consumersByComponent.forEach((componentName, consumerIds) -> {
            var component = resolveComponent(componentName, componentType,
                    sourceBundleId, sourceInstanceId, sourceBundleVersion);
            for (String id : consumerIds) {
                var c = new Consumer();
                c.setInstanceId(sourceInstanceId);
                c.setConsumerId(id);
                c.setComponent(component);
                c.setIdentifier(c.getInstanceId() + "_" + c.getConsumerId());
                out.add(c);
            }
        });
    }

    private Component resolveComponent(String componentName,
                                       ComponentType componentType,
                                       String sourceBundleId,
                                       String sourceInstanceId,
                                       long sourceBundleVersion) {
        return componentRepository.findById(componentName).orElseGet(() -> {
            // Registration can race auto-discovery (the two run under different lock
            // keys on different bus threads), and a stale NodeLeft can delete the
            // component rows between the two. The old getReferenceById proxy blew up
            // at flush time and the whole registration was silently dropped; recreate
            // an ephemeral row instead — the next discovery pass backfills its metadata.
            logger.warn("Component %s not found while registering consumers for bundle: %s — creating an ephemeral one"
                    .formatted(componentName, sourceBundleId));
            var bundle = bundleRepository.findById(sourceBundleId).orElseGet(() ->
                    bundleRepository.save(new Bundle(sourceBundleId, sourceBundleVersion,
                            "", "", "", "", sourceInstanceId, true, Instant.now())));
            var component = new Component();
            component.setComponentName(componentName);
            component.setBundle(bundle);
            component.setComponentType(componentType);
            component.setUpdatedAt(Instant.now());
            return componentRepository.save(component);
        });
    }

    /**
     * Returns a list of all consumers in the system.
     *
     * @return a list of Consumer objects representing the consumers in the system.
     */
    public List<Consumer> findAll() {
        return consumerRepository.findAll();
    }

    /**
     * Retrieves the consumer status from the nodes in the system.
     * <p>
     * This method takes in the consumer ID and the message bus, and retrieves the consumer status from the nodes in the system.
     * The consumer status is obtained by finding the instances associated with the consumer ID, and then selecting the first available node
     * from the current view of the message bus. A request is sent to the selected node to fetch the consumer status.
     *
     * @param consumerId the ID of the consumer for which the status is to be fetched, as a String.
     * @param messageBus the message bus used to send the request to the node, as a MessageBus object.
     * @return a CompletableFuture representing the result of the asynchronous operation. The CompletableFuture will complete with a ConsumerFetchStatusResponseMessage,
     *         which contains the status of the consumer's fetch operation, or an exception if an error occurs.
     * @throws Exception if an error occurs during the retrieval of the consumer status.
     */
    public CompletableFuture<ConsumerFetchStatusResponseMessage> getConsumerStatusFromNodes(String consumerId, BusFacade busFacade) throws Exception {
        var consumers =  consumerRepository.findAllByConsumerId(consumerId);
        var instances = consumers.stream()
                .map(Consumer::getInstanceId)
                .collect(Collectors.toSet());
        var address =  busFacade.currentView().stream().filter(n ->
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
        busFacade.forward(address, request, (c) -> {
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

    /**
     * Deletes all consumers associated with a specific instance ID.
     * <p>
     * This method deletes all consumers from the consumer repository that are associated with the given instance ID.
     *
     * @param instanceId the ID of the instance for which all consumers should be deleted, as a String.
     */
    public void clearInstance(String instanceId) {
        consumerRepository.deleteAllByInstanceId(instanceId);
    }

    /**
     * Sets the retry flag for a consumer event.
     * <p>
     * This method allows you to set the retry flag for a specific consumer event by providing the consumer ID, event sequence number,
     * retry flag, and a MessageBus object. The consumer ID is used to fetch the consumer's status from the nodes in the system,
     * and the event sequence number is used to identify the specific event. The retry flag determines whether the event should
     * be retried or not. The method returns a CompletableFuture<ConsumerResponseMessage> that represents the result of the operation.
     *
     * @param consumerId          the ID of the consumer for which the retry flag is to be set, as a String.
     * @param eventSequenceNumber the sequence number of the event for which the retry flag is to be set, as a long.
     * @param retry               a boolean value indicating whether the retry flag should be set or not.
     * @param messageBus          the MessageBus object used to forward the request to the appropriate node.
     * @return a CompletableFuture<ConsumerResponseMessage> representing the result of the operation. The CompletableFuture will
     *         complete with a ConsumerResponseMessage that contains a success flag indicating whether the retry flag was
     *         successfully set or not.
     * @throws Exception if an error occurs during the retrieval of the consumer status or forwarding of the request.
     */
    public CompletableFuture<ConsumerResponseMessage> setRetryForConsumerEvent(String consumerId, long eventSequenceNumber, boolean retry, BusFacade busFacade) throws Exception {
        var consumers =  consumerRepository.findAllByConsumerId(consumerId);
        var instances = consumers.stream()
                .map(Consumer::getInstanceId)
                .collect(Collectors.toSet());
        var address =  busFacade.currentView().stream().filter(n ->
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
        var future = new CompletableFuture<ConsumerResponseMessage>();
        busFacade.forward(address, request, (c) -> {
            if(c.getBody() instanceof ConsumerResponseMessage resp){
                future.complete(resp);
            } else if (c.getBody() instanceof ExceptionWrapper e) {
                future.completeExceptionally(e.toException());
            }else{
                future.completeExceptionally(new RuntimeException("Invalid response from component while set retry to consumer"));
            }
        });
        return future;
    }

    /**
     * Consumes dead messages from the dead queue for a specific consumer.
     *
     * @param consumerId the ID of the consumer for which dead messages should be consumed, as a String.
     * @param messageBus the MessageBus object used to consume the dead messages, as a MessageBus object.
     * @return a CompletableFuture representing the result of the asynchronous operation. The CompletableFuture will complete with a ConsumerResponseMessage
     *         indicating whether the dead messages were successfully consumed or not.
     * @throws Exception if an error occurs during the consumption of the dead messages.
     */
    public CompletableFuture<ConsumerResponseMessage> consumeDeadQueue(String consumerId, BusFacade busFacade) throws Exception {
        var consumers =  consumerRepository.findAllByConsumerId(consumerId);
        var instances = consumers.stream()
                .map(Consumer::getInstanceId)
                .collect(Collectors.toSet());
        var address =  busFacade.currentView().stream().filter(n ->
                        instances.contains(n.instanceId())).findFirst()
                .orElseThrow();
        var request  = new EventoRequest();
        request.setCorrelationId(UUID.randomUUID().toString());
        request.setTimestamp(System.currentTimeMillis());
        request.setSourceBundleId("evento-server");
        request.setSourceInstanceId(eventoServerInstanceId);
        request.setSourceBundleVersion(0);
        request.setBody(new ConsumerProcessDeadQueueRequestMessage(consumerId, consumers.getFirst().getComponent().getComponentType()));
        var future = new CompletableFuture<ConsumerResponseMessage>();
        busFacade.forward(address, request, (c) -> {
            if(c.getBody() instanceof ConsumerResponseMessage resp){
                future.complete(resp);
            } else if (c.getBody() instanceof ExceptionWrapper e) {
                future.completeExceptionally(e.toException());
            }else{
                future.completeExceptionally(new RuntimeException("Invalid response from component while consuming dead queue"));
            }
        });
        return future;
    }

    /**
     * Deletes a dead event from an event consumer.
     * <p>
     * This method takes in the ID of the event consumer, the sequence number of the dead event,
     * and the message bus. It deletes the dead event from the consumer by sending a request to the
     * appropriate node in the system via the message bus. The method returns a CompletableFuture
     * with a ConsumerResponseMessage indicating whether the dead event was successfully deleted or not.
     *
     * @param consumerId           the ID of the event consumer, as a String.
     * @param eventSequenceNumber  the sequence number of the dead event, as a long.
     * @param messageBus           the MessageBus object used to forward the request to the node, as a MessageBus.
     * @return a CompletableFuture with a ConsumerResponseMessage indicating whether the dead event was successfully deleted or not.
     * @throws Exception if an error occurs during the deletion of the dead event.
     */
    public CompletableFuture<ConsumerResponseMessage> deleteDeadEventFromEventConsumer(
            String consumerId,
            long eventSequenceNumber,
            BusFacade busFacade) throws Exception {
        var consumers =  consumerRepository.findAllByConsumerId(consumerId);
        var instances = consumers.stream()
                .map(Consumer::getInstanceId)
                .collect(Collectors.toSet());
        var address =  busFacade.currentView().stream().filter(n ->
                        instances.contains(n.instanceId())).findFirst()
                .orElseThrow();
        var request  = new EventoRequest();
        request.setCorrelationId(UUID.randomUUID().toString());
        request.setTimestamp(System.currentTimeMillis());
        request.setSourceBundleId("evento-server");
        request.setSourceInstanceId(eventoServerInstanceId);
        request.setSourceBundleVersion(0);
        request.setBody(new ConsumerDeleteDeadEventRequestMessage(consumerId, consumers.getFirst().getComponent().getComponentType(),
                eventSequenceNumber));
        var future = new CompletableFuture<ConsumerResponseMessage>();
        busFacade.forward(address, request, (c) -> {
            if(c.getBody() instanceof ConsumerResponseMessage resp){
                future.complete(resp);
            } else if (c.getBody() instanceof ExceptionWrapper e) {
                future.completeExceptionally(e.toException());
            }else{
                future.completeExceptionally(new RuntimeException("Invalid response from component while deleting dead event from consumer"));
            }
        });
        return future;
    }
}
