package com.evento.lab.cluster.command;

import com.evento.application.EventoBundle;
import com.evento.lab.cluster.command.bundle.OrderAggregate;
import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.FrameworkFeatures;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.store.SideEffectStore;

import java.util.Map;

/**
 * Bundle {@code order-command}: the order aggregate, the payment "provider"
 * and the price list. No consumers, so {@code EVENTO_CONTEXTS} does not apply.
 * Deployed once per cell with that cell's broker first in its address list and
 * its local app database, which holds the {@code payments} side-effect table
 * and, with the G5 inbox fix, the outcomes of keyed service commands
 * ({@code evento_v2_service_outcome}). {@code EVENTO_SERVICE_OUTCOME_STORE=off}
 * runs without the outcome store (the residual window open again).
 */
public class OrderCommandBundle implements BundleDefinition {

    private final BundleDeployment deployment;
    private final LabDatabases databases;

    public OrderCommandBundle(BundleDeployment deployment, LabDatabases databases) {
        this.deployment = deployment;
        this.databases = databases;
    }

    @Override
    public Class<?> anchor() {
        return OrderAggregate.class;
    }

    @Override
    public Map<Class<?>, Object> injectables() {
        return Map.of(SideEffectStore.class, new SideEffectStore(databases.app(), deployment.instanceId()));
    }

    @Override
    public boolean usesConsumers() {
        return false;
    }

    @Override
    public void configure(EventoBundle.Builder builder, BundleDeployment deployment, FrameworkFeatures features)
            throws Exception {
        var mode = System.getenv().getOrDefault("EVENTO_SERVICE_OUTCOME_STORE", "jdbc");
        if (!"off".equalsIgnoreCase(mode) && FrameworkFeatures.serviceOutcomesAvailable()) {
            // the outcome table lives next to the payments, in the cell's app database
            databases.initialiseFrameworkSchemaOnApp();
        }
        features.serviceOutcomeStore(builder, databases.app(), mode);
    }
}
