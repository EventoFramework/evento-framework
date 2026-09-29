package com.evento.lab.cluster.observer;

import com.evento.lab.cluster.support.store.SideEffectStore;

/**
 * Where the observer records its side effect, and under which lane name
 * (the deployment's bundle id).
 */
public record ObserverLane(SideEffectStore sideEffects, String name) {
}
