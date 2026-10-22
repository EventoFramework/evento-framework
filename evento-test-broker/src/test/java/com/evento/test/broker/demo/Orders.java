package com.evento.test.broker.demo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** What the projector has seen, shared with the projection that answers queries. */
public final class Orders {
    public static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private Orders() {}
}
