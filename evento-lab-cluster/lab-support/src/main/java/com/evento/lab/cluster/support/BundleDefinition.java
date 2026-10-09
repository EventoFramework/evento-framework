package com.evento.lab.cluster.support;

import com.evento.application.EventoBundle;

import java.util.List;
import java.util.Map;

/**
 * What a bundle module contributes to the shared runner: where its components
 * are, what they need injected, and how the deployment's configuration maps
 * onto them (typically {@code setComponentContexts}). Everything else (bus
 * endpoints, identity, zone, JDBC consumer state, health) is common and lives
 * in {@link BundleRunner}.
 */
public interface BundleDefinition {

    /** A class of the bundle's component package; the package is scanned, sub-packages included. */
    Class<?> anchor();

    /** Constructor arguments of the components, by type. */
    Map<Class<?>, Object> injectables();

    /** Whether the bundle has consumers (projectors, sagas, observers) and needs the JDBC consumer state. */
    boolean usesConsumers();

    /** Apply the deployment's configuration to the builder: per-component contexts and the like. */
    default void configure(EventoBundle.Builder builder, BundleDeployment deployment) {
    }

    /**
     * {@link #configure(EventoBundle.Builder, BundleDeployment)} with the reflective shim, for
     * framework features the pre-fix images lack (outcomes it records show in {@code /whoami}).
     */
    default void configure(EventoBundle.Builder builder, BundleDeployment deployment, FrameworkFeatures features)
            throws Exception {
        configure(builder, deployment);
    }

    /**
     * The consumer engines this deployment will start, component name to
     * contexts (an empty list means the component runs no engine at all).
     * Shown by {@code /whoami} and logged at start-up.
     */
    default Map<String, List<String>> consumerPlan(BundleDeployment deployment) {
        return Map.of();
    }
}
