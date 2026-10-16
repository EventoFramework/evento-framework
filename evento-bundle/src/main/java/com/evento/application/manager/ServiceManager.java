package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ServiceReference;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.messaging.outcome.DuplicateServiceOutcomeException;
import com.evento.common.messaging.outcome.ServiceCommandOutcomeStore;
import com.evento.common.messaging.outcome.ServiceCommandOutcomes;
import com.evento.common.messaging.outcome.StoredOutcome;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.exceptions.IdempotencyKeyMismatchException;
import com.evento.common.modeling.exceptions.HandlerNotFoundException;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The ServiceManager class is responsible for managing service commands
 * and their associated handlers. It extends the ReceiverComponentManager class
 * and implements the required methods for parsing and handling the service commands.
 */
public class ServiceManager extends ReceiverComponentManager<ServiceCommandMessage, ServiceReference> {
    private static final Logger logger = LoggerFactory.getLogger(ServiceManager.class);

    private volatile ServiceCommandOutcomeStore outcomeStore;
    private final java.util.concurrent.atomic.AtomicLong replayed = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Creates a new instance of ServiceManager with the specified parameters.
     *
     * @param bundleId                   the ID of the bundle associated with the ServiceManager
     * @param gatewayTelemetryProxy      a function that maps a service name and a Message object to a GatewayTelemetryProxy
     * @param tracingAgent               the tracing agent to be used by the ServiceManager
     */
    public ServiceManager(String bundleId, BiFunction<String, Message<?>,
            GatewayTelemetryProxy> gatewayTelemetryProxy,
                          TracingAgent tracingAgent,
                          MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, messageHandlerInterceptor);
    }

    /**
     * Parses the classes annotated with @Service in the scanned classes and registers their command handlers.
     *
     * @param scan             the classes of the bundle's package
     * @param findInjectableObject    a function that finds an instance of the specified class to be injected into the service objects
     * @throws InvocationTargetException if an error occurs while invoking a method or constructor
     * @throws InstantiationException    if an error occurs while creating an instance of a service object
     * @throws IllegalAccessException    if access to a class, method, or field is denied
     */
    @Override
    public void parse(ComponentScan scan, Function<Class<?>, Object> findInjectableObject)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> aClass : scan.annotatedWith(Service.class)) {
            var serviceReference = new ServiceReference(createComponentInstance(aClass, findInjectableObject));
            for (String command : serviceReference.getRegisteredCommands()) {
                getHandlers().put(command, serviceReference);
                logger.info("Service command handler for %s found in %s".formatted(command, serviceReference.getRef().getClass().getName()));
            }
        }
    }

    /**
     * Handles a service command by invoking the appropriate handler for the command name.
     *
     * @param c the ServiceCommandMessage object containing the command name and payload
     * @return a ServiceEventMessage object representing the result of the command execution
     * @throws HandlerNotFoundException   if no handler is found for the specified command name
     * @throws Throwable                  if an error occurs during command handling
     */
    @Override
    public ServiceEventMessage handle(ServiceCommandMessage c) throws Throwable {
        var handler = getHandlers().get(c.getCommandName());
        if (handler == null)
            throw new HandlerNotFoundException("No handler found for %s in %s"
                    .formatted(c.getCommandName(), getBundleId()));
        var key = c.getIdempotencyKey();
        var store = outcomeStore;
        if (store == null || key == null) {
            return invoke(handler, c);
        }
        return handleKeyed(handler, c, key, store);
    }

    /**
     * The outcome store this manager answers keyed commands from (G5 residual window); {@code null}
     * (the default) runs every command, as before.
     */
    public void setOutcomeStore(ServiceCommandOutcomeStore outcomeStore) {
        this.outcomeStore = outcomeStore;
    }

    public ServiceCommandOutcomeStore getOutcomeStore() {
        return outcomeStore;
    }

    /**
     * A keyed command with an outcome store: answered from the store when its key has an outcome
     * (the handler already ran, e.g. its event could not be stored and the broker retried), else
     * run, and its outcome recorded before the reply leaves (by the handler in its own
     * transaction through {@link ServiceCommandOutcomes#recordWith}, or here after it returns).
     * A store that fails on the lookup fails the command (nothing ran, the broker releases its
     * claim and a retry can run it); one that fails on the record after a successful run does not
     * (the broker stores the event, which answers later retries), it is logged.
     */
    private ServiceEventMessage handleKeyed(ServiceReference handler, ServiceCommandMessage c, String key,
                                            ServiceCommandOutcomeStore store) throws Throwable {
        var commandType = c.getCommandName();
        var recorded = store.find(key);
        if (recorded.isPresent()) {
            return replay(recorded.get(), c, key, "before_handler");
        }
        var outcomes = new ServiceCommandOutcomes(key, commandType, store);
        ServiceEventMessage result;
        try (var ignored = ServiceCommandOutcomes.bind(outcomes)) {
            result = invoke(handler, c);
        } catch (Throwable t) {
            if (DuplicateServiceOutcomeException.isCauseOf(t)) {
                // Another execution of the key committed its outcome (and its side effects) first;
                // this one's transaction rolled back. Answer what was recorded.
                var first = store.find(key);
                if (first.isPresent()) return replay(first.get(), c, key, "concurrent_execution");
            }
            throw t;
        }
        if (!outcomes.isRecorded()) {
            try {
                var payload = result.getSerializedPayload();
                byte[] serialized = payload == null || payload.getObjectClass() == null
                        || payload.getSerializedObject() == null ? null
                        : payload.getSerializedObject().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (!store.record(key, commandType, serialized)) {
                    var first = store.find(key);
                    logger.warn("event=service_outcome_duplicate_execution bundle={} commandType={} idempotencyKey={} "
                                    + "reason=\"another execution of the key recorded its outcome first; its answer is "
                                    + "returned, but this handler's side effects happened too (record the outcome in the "
                                    + "handler's transaction to make them exactly-once)\"",
                            getBundleId(), commandType, key);
                    if (first.isPresent()) return replay(first.get(), c, key, "record_conflict");
                }
            } catch (RuntimeException e) {
                logger.warn("event=service_outcome_record_failed bundle={} commandType={} idempotencyKey={} error=\"{}\" "
                                + "reason=\"the handler ran; a retry is answered by the broker once the event is "
                                + "stored, but runs the handler again if the event is never stored\"",
                        getBundleId(), commandType, key, e.toString());
            }
        }
        return result;
    }

    private ServiceEventMessage replay(StoredOutcome outcome, ServiceCommandMessage c, String key, String stage) {
        var commandType = c.getCommandName();
        if (!commandType.equals(outcome.commandType())) {
            logger.warn("event=idempotency_key_mismatch bundle={} commandType={} idempotencyKey={} recordedCommandType={} "
                    + "stage=service_outcome", getBundleId(), commandType, key, outcome.commandType());
            throw new IdempotencyKeyMismatchException("idempotency key reused by a different command: '" + key
                    + "' has an outcome recorded for " + outcome.commandType() + ", not for " + commandType
                    + "; nothing was executed");
        }
        logger.info("event=service_outcome_replayed bundle={} commandType={} idempotencyKey={} stage={} recordedAt={}",
                getBundleId(), commandType, key, stage, outcome.recordedAt());
        replayed.incrementAndGet();
        return new ServiceEventMessage(ServiceCommandOutcomes.deserialize(outcome.serializedResult()));
    }

    /** Keyed commands answered from the outcome store without running their handler. */
    public long outcomesReplayed() {
        return replayed.get();
    }

    private ServiceEventMessage invoke(ServiceReference handler, ServiceCommandMessage c) throws Throwable {
        var proxy = getGatewayTelemetryProxy().apply(handler.getComponentName(), c);
        return getTracingAgent().track(c, handler.getComponentName(),
                null,
                () -> {
                    var event = handler.invoke(
                            c,
                            proxy,
                            proxy,
                            getMessageHandlerInterceptor()
                    );
                    var em = new ServiceEventMessage(event);
                    getTracingAgent().correlate(c, em);
                    proxy.sendInvocationsMetric();
                    return em;
                });
    }
}
