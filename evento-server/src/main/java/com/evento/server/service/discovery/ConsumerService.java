package com.evento.server.service.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.consumer.*;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.bus.BusFacade;
import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Consumer;
import com.evento.server.domain.repository.core.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final CatalogWriter catalogWriter;
    /** Null in unit tests: the registration then runs without a surrounding transaction. */
    private final TransactionTemplate tx;

    private final PgDistributedLock distributedLock;

    public ConsumerService(ComponentRepository componentRepository,
                           ConsumerRepository consumerRepository,
                           BundleRepository bundleRepository,
                           @Value("${evento.server.instance.id}") String eventoServerInstanceId,
                           PgDistributedLock distributedLock,
                           CatalogWriter catalogWriter,
                           PlatformTransactionManager transactionManager) {
        this.componentRepository = componentRepository;
        this.consumerRepository = consumerRepository;
        this.bundleRepository = bundleRepository;
        this.eventoServerInstanceId = eventoServerInstanceId;
        this.catalogWriter = catalogWriter;
        this.tx = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        // The shared, pool-backed lock (DistributedLockConfiguration); null = JVM-local only
        // (embedded brokers and unit tests).
        this.distributedLock = distributedLock != null ? distributedLock : new PgDistributedLock(null);
    }

    /**
     * The advisory-lock key under which every write to a bundle's catalog rows runs.
     * Discovery, node-leave cleanup and consumer registration all create or delete the
     * same bundle and component rows, so they must serialize on one key. Consumer
     * registration used to take an instance-scoped key and raced the other two.
     */
    static String catalogLockKey(String bundleId) {
        return "DISCOVERY:" + bundleId;
    }

    static void inTransaction(TransactionTemplate tx, Runnable work) {
        if (tx == null) {
            work.run();
        } else {
            tx.executeWithoutResult(status -> work.run());
        }
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
            this.distributedLock.lockedArea(catalogLockKey(sourceBundleId), () -> inTransaction(tx, () -> {
                logger.info("Discovering consumers in bundle: %s and instance: %s".formatted(sourceBundleId, sourceInstanceId));
                var consumers = new ArrayList<Consumer>();
                collectConsumers(cr.getProjectorConsumers(), ComponentType.Projector,
                        sourceBundleId, sourceInstanceId, sourceBundleVersion, consumers);
                collectConsumers(cr.getSagaConsumers(), ComponentType.Saga,
                        sourceBundleId, sourceInstanceId, sourceBundleVersion, consumers);
                collectConsumers(cr.getObserverConsumers(), ComponentType.Observer,
                        sourceBundleId, sourceInstanceId, sourceBundleVersion, consumers);
                // All rows of one registration land together or not at all: a half-written
                // set would show some of the instance's consumers and silently hide the rest.
                catalogWriter.upsertConsumers(consumers);
            }));
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
            // Registration can run before discovery has written the component, and a
            // stale NodeLeft can delete the component rows between the two. The old
            // getReferenceById proxy blew up at flush time and the whole registration
            // was silently dropped; recreate an ephemeral row instead, and the next
            // discovery pass backfills its metadata. The inserts are ON CONFLICT DO
            // NOTHING, so a concurrent discovery on another server (or of another bundle
            // sharing the component name) cannot turn this into a duplicate-key failure.
            if (catalogWriter.insertBundleIfAbsent(sourceBundleId, sourceBundleVersion,
                    "", "", "", "", sourceInstanceId, true)) {
                logger.warn("Bundle %s not found while registering consumers: created an ephemeral one"
                        .formatted(sourceBundleId));
            }
            if (catalogWriter.insertComponentIfAbsent(componentName, componentType, sourceBundleId,
                    null, null, null, null)) {
                logger.warn("Component %s not found while registering consumers for bundle: %s: created an ephemeral one"
                        .formatted(componentName, sourceBundleId));
            }
            return componentRepository.findById(componentName).orElseThrow(() -> new IllegalStateException(
                    "Component %s vanished while registering consumers for bundle %s"
                            .formatted(componentName, sourceBundleId)));
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
