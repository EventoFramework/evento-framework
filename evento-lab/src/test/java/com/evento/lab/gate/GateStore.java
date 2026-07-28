package com.evento.lab.gate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Static assertion surface for {@link StartupGateIT}. The gate blocks the projector's
 * event handler so the test controls exactly when the consumer can reach head.
 */
public final class GateStore {

    public static volatile CountDownLatch gate = new CountDownLatch(0);
    public static final List<String> applied = new CopyOnWriteArrayList<>();

    private GateStore() {}

    public static void reset() {
        gate = new CountDownLatch(0);
        applied.clear();
    }

    public static void handle(String orderId) throws InterruptedException {
        gate.await(30, TimeUnit.SECONDS);
        applied.add(orderId);
    }
}
