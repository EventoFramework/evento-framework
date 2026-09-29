package com.evento.lab.cluster.command;

import com.evento.lab.cluster.command.bundle.OrderAggregate;
import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.store.SideEffectStore;

import java.util.Map;

/**
 * Bundle {@code order-command}: the order aggregate, the payment "provider"
 * and the price list. No consumers, so {@code EVENTO_CONTEXTS} does not apply.
 * Deployed once per cell with that cell's broker first in its address list and
 * its local app database, which holds the {@code payments} side-effect table.
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
}
