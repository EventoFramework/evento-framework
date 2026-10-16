package com.evento.lab.ryw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/** The read model of the read-your-writes scenario, and the latch that holds its projector back. */
public final class RywStore {

    public static final Map<String, String> notes = new ConcurrentHashMap<>();
    /** Open by default; a test closes it to make the projector lag behind the writes. */
    public static volatile CountDownLatch gate = new CountDownLatch(0);

    private RywStore() {}

    public static void reset() {
        notes.clear();
        gate = new CountDownLatch(0);
    }
}
