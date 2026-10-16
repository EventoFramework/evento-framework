package com.evento.lab.streaming;

import java.util.concurrent.atomic.AtomicInteger;

/** What the lazy source did: how many views it produced, how many times it opened and closed. */
public final class StreamingStore {

    public static final AtomicInteger produced = new AtomicInteger();
    public static final AtomicInteger opened = new AtomicInteger();
    public static final AtomicInteger closed = new AtomicInteger();

    private StreamingStore() {}

    public static void reset() {
        produced.set(0);
        opened.set(0);
        closed.set(0);
    }
}
