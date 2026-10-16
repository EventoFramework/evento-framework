package com.evento.transport.netty;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs tasks one at a time, in submission order, on a shared executor, without holding
 * a thread of its own between bursts.
 *
 * <p>Every inbound frame is dispatched to the business executor so that no handler ever
 * runs on the event loop. For independent requests and replies that is the point: they
 * run concurrently. The frames of a streamed reply ({@code StreamData}) must instead
 * reach the application in the order they were read, and two virtual threads started
 * one after the other carry no such guarantee. One lane per channel keeps those frames
 * in order (the broker then forwards them in order, the caller consumes them in order)
 * while every other frame keeps its own thread.
 */
final class SerialLane implements Executor {

    private final Executor executor;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean();

    SerialLane(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
        schedule();
    }

    private void schedule() {
        if (draining.compareAndSet(false, true)) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException rejected) {
                draining.set(false);
                throw rejected;
            }
        }
    }

    private void drain() {
        try {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable ignored) {
                    // The task reports its own failures; a throwing task must not stall the lane.
                }
            }
        } finally {
            draining.set(false);
            // A task added after the last poll but before the flag dropped would wait for
            // the next submission without this second look.
            if (!tasks.isEmpty()) schedule();
        }
    }
}
