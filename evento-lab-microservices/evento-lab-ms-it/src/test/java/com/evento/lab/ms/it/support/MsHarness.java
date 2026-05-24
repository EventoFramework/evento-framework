package com.evento.lab.ms.it.support;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.ms.command.LabMsCommandApplication;
import com.evento.lab.ms.observer.LabMsObserverApplication;
import com.evento.lab.ms.query.LabMsQueryApplication;
import com.evento.lab.ms.query.projector.OrderProjector;
import com.evento.lab.ms.saga.LabMsSagaApplication;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Test harness that starts multiple consumer bundles against the same ephemeral broker.
 */
public final class MsHarness implements AutoCloseable {

    private final MsEmbeddedBroker broker;
    private final MsTestEventStore eventStore;
    private EventoBundle queryBundle;
    private EventoBundle sagaBundle;
    private EventoBundle observerBundle;
    private EventoBundle commandBundle;
    private final List<EventoBundle> contextBundles = new ArrayList<>();

    public MsHarness() throws Exception {
        broker = new MsEmbeddedBroker();
        eventStore = new MsTestEventStore(broker.port());
    }

    public MsHarness withQueryBundle() throws Exception {
        queryBundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsQueryApplication.class.getPackage())
                .setBundleId("lab-ms-query")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        return this;
    }

    public MsHarness withSagaBundle() throws Exception {
        sagaBundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsSagaApplication.class.getPackage())
                .setBundleId("lab-ms-saga")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        return this;
    }

    public MsHarness withObserverBundle() throws Exception {
        observerBundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsObserverApplication.class.getPackage())
                .setBundleId("lab-ms-observer")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        return this;
    }

    public MsHarness withCommandBundle() throws Exception {
        commandBundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsCommandApplication.class.getPackage())
                .setBundleId("lab-ms-command")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .start();
        return this;
    }

    public MsHarness withQueryBundleForContext(String context) throws Exception {
        var bundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsQueryApplication.class.getPackage())
                .setBundleId("lab-ms-query-" + context.toLowerCase())
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setComponentContexts(OrderProjector.class, context)
                .start();
        contextBundles.add(bundle);
        return this;
    }

    public MsEmbeddedBroker broker() { return broker; }
    public MsTestEventStore eventStore() { return eventStore; }
    public EventoBundle queryBundle() { return queryBundle; }
    public EventoBundle sagaBundle() { return sagaBundle; }
    public EventoBundle observerBundle() { return observerBundle; }
    public EventoBundle commandBundle() { return commandBundle; }
    public List<EventoBundle> contextBundles() { return contextBundles; }

    @Override
    public void close() throws Exception {
        Duration timeout = Duration.ofSeconds(10);
        if (queryBundle != null) queryBundle.getEngineSupervisor().stop(timeout);
        if (sagaBundle != null) sagaBundle.getEngineSupervisor().stop(timeout);
        if (observerBundle != null) observerBundle.getEngineSupervisor().stop(timeout);
        if (commandBundle != null) commandBundle.getEngineSupervisor().stop(timeout);
        for (var bundle : contextBundles) {
            bundle.getEngineSupervisor().stop(timeout);
        }
        eventStore.close();
        broker.close();
    }
}
