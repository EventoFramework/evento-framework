package com.evento.lab.cluster.observer;

import com.evento.application.EventoBundle;
import com.evento.lab.cluster.observer.bundle.NotificationObserver;
import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.store.SideEffectStore;

import java.util.List;
import java.util.Map;

/**
 * Bundle {@code order-observer}: one {@link NotificationObserver}, one engine
 * per context of {@code EVENTO_CONTEXTS}, taken as configured ({@code *}, a
 * list of regions, or {@code ALL}). The default topology runs a single
 * {@code *} deployment; per-cell regional deployments are the same image with
 * other contexts (see the compose profile {@code regional-observers}).
 */
public class OrderObserverBundle implements BundleDefinition {

    private final BundleDeployment deployment;
    private final LabDatabases databases;

    public OrderObserverBundle(BundleDeployment deployment, LabDatabases databases) {
        this.deployment = deployment;
        this.databases = databases;
    }

    @Override
    public Class<?> anchor() {
        return NotificationObserver.class;
    }

    @Override
    public Map<Class<?>, Object> injectables() {
        return Map.of(ObserverLane.class, new ObserverLane(
                new SideEffectStore(databases.app(), deployment.instanceId()), deployment.bundleId()));
    }

    @Override
    public boolean usesConsumers() {
        return true;
    }

    @Override
    public Map<String, List<String>> consumerPlan(BundleDeployment d) {
        return Map.of(NotificationObserver.class.getSimpleName(), d.requireContexts().contexts());
    }

    @Override
    public void configure(EventoBundle.Builder builder, BundleDeployment d) {
        builder.setComponentContexts(NotificationObserver.class,
                d.requireContexts().contexts().toArray(String[]::new));
    }
}
