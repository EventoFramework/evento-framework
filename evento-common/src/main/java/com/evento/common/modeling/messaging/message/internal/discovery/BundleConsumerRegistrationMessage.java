package com.evento.common.modeling.messaging.message.internal.discovery;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

/**
 * The BundleConsumerRegistrationMessage class represents a message used to register bundle consumers.
 * It is a serializable class that contains three HashMaps representing different types of consumers -
 * projector consumers, saga consumers, and observer consumers.
 * <p>
 * Each HashMap maps a String key to a HashSet of Strings. The keys represent the bundle names, and
 * the HashSet represents the set of consumer names associated with each bundle.
 * <p>
 * This class provides getter and setter methods for each HashMap property, allowing access and modification
 * of the consumer registration information.
 */
public class BundleConsumerRegistrationMessage implements Serializable {

    private HashMap<String, HashSet<String>> projectorConsumers;
    private HashMap<String, HashSet<String>> sagaConsumers;
    private HashMap<String, HashSet<String>> observerConsumers;

    /**
     * Returns the HashMap that maps bundle names to sets of consumer names for projector consumers.
     *
     * @return the HashMap representing the projector consumers, where each key is a bundle name, and
     *         the corresponding value is a set of consumer names associated with the bundle
     */
    public HashMap<String, HashSet<String>> getProjectorConsumers() {
        return projectorConsumers;
    }

    /**
     * Sets the projectorConsumers property with the specified HashMap.
     * The projectorConsumers property maps bundle names to sets of consumer names for projector consumers.
     * Each key in the HashMap represents a bundle name, and the corresponding value is a HashSet of consumer names associated with the bundle.
     *
     * @param projectorConsumers the HashMap representing the projector consumers to be set
     */
    public void setProjectorConsumers(HashMap<String, HashSet<String>> projectorConsumers) {
        this.projectorConsumers = projectorConsumers;
    }

    /**
     * Returns the HashMap that maps bundle names to sets of consumer names for saga consumers.
     *
     * @return the HashMap representing the saga consumers, where each key is a bundle name, and
     *         the corresponding value is a set of consumer names associated with the bundle
     */
    public HashMap<String, HashSet<String>> getSagaConsumers() {
        return sagaConsumers;
    }

    /**
     * Sets the {@code sagaConsumers} property with the specified HashMap.
     * The {@code sagaConsumers} property maps bundle names to sets of consumer names for saga consumers.
     * Each key in the HashMap represents a bundle name, and the corresponding value is a HashSet of consumer names associated with the bundle.
     *
     * @param sagaConsumers the HashMap representing the saga consumers to be set
     */
    public void setSagaConsumers(HashMap<String, HashSet<String>> sagaConsumers) {
        this.sagaConsumers = sagaConsumers;
    }

    /**
     * Returns the HashMap that maps bundle names to sets of consumer names for observer consumers.
     *
     * @return the HashMap representing the observer consumers, where each key is a bundle name,
     *         and the corresponding value is a set of consumer names associated with the bundle
     */
    public HashMap<String, HashSet<String>> getObserverConsumers() {
        return observerConsumers;
    }

    /**
     * Sets the observerConsumers property with the specified HashMap.
     * The observerConsumers property maps bundle names to sets of consumer names for observer consumers.
     * Each key in the HashMap represents a bundle name, and the corresponding value is a HashSet of consumer names associated with the bundle.
     *
     * @param observerConsumers the HashMap representing the observer consumers to be set
     */
    public void setObserverConsumers(HashMap<String, HashSet<String>> observerConsumers) {
        this.observerConsumers = observerConsumers;
    }
}
