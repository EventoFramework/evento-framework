package com.evento.application.consumer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * When a consumer at the head of the event stream fetches again.
 *
 * <p>Without notices (a broker that did not accept
 * {@link com.evento.transport.HandshakeProtocol#CAPABILITY_HEAD}) a consumer at the head pauses a
 * few milliseconds and fetches again, as in 2.x: with a hundred consumers that is thousands of
 * empty fetches a second, each an event-store query, and the fetches that do bring events wait
 * behind them on the broker. With notices the broker says when its store has grown
 * ({@link #announce()}), and a consumer at the head waits for that, up to its fetch delay as a
 * safety net.
 *
 * <p>Each engine registers its wake-up semaphore with the names of the events it handles. A notice
 * names the events appended since the last one, and releases the engines that handle one of them
 * (a notice that names none releases all): the others have nothing new to apply, and their fetch
 * would only queue on the broker in front of those that do. A notice that arrives while an engine
 * is fetching leaves its permit, so the next pause returns at once: no append is missed between a
 * fetch and the pause after it.
 *
 * <p>The other engines are woken too, but late and once per {@link #DEFAULT_LAZY_MS} however many
 * notices come: their position must still move past the events they do not handle. Another replica
 * of the consumer reads that position from the store for a read-your-writes query whose token is
 * such an event, and cannot wake the replica that holds the lane; left to the fetch delay (which
 * applications may have set high, as it used to apply only after errors) that query could wait
 * seconds or time out. An event whose stored name no handler declares (renamed through an
 * upcaster) reaches its consumer the same way.
 */
public final class HeadSignal {

    /** How late an engine is woken by notices about events it does not handle. */
    public static final long DEFAULT_LAZY_MS = 500;

    /** An engine's event names (empty: any) and whether a late wake-up is already scheduled for it. */
    private record Registration(Set<String> eventNames, java.util.concurrent.atomic.AtomicBoolean lateQueued) {}

    private final BooleanSupplier notified;
    private final long lazyMs;
    private final java.util.Map<Semaphore, Registration> wakeups = new ConcurrentHashMap<>();
    private volatile java.util.concurrent.ScheduledExecutorService lateWakeups;

    /** @param notified whether the current session gets head notices */
    public HeadSignal(BooleanSupplier notified) {
        this(notified, DEFAULT_LAZY_MS);
    }

    /**
     * @param notified whether the current session gets head notices
     * @param lazyMs   how late a notice about events an engine does not handle wakes it; 0: never
     */
    public HeadSignal(BooleanSupplier notified, long lazyMs) {
        this.notified = notified == null ? () -> false : notified;
        this.lazyMs = Math.max(0, lazyMs);
    }

    /** Never notified: the consumers poll (engine tests, brokers without the capability). */
    public static HeadSignal polling() {
        return new HeadSignal(() -> false);
    }

    /** Whether the current session gets head notices. */
    public boolean notified() {
        return notified.getAsBoolean();
    }

    /** @param eventNames the events the engine handles; empty or null: woken by every notice */
    public void register(Semaphore wakeup, java.util.Collection<String> eventNames) {
        wakeups.put(wakeup, new Registration(eventNames == null ? Set.of() : Set.copyOf(eventNames),
                new java.util.concurrent.atomic.AtomicBoolean()));
    }

    /** Woken by every notice. */
    public void register(Semaphore wakeup) {
        register(wakeup, Set.of());
    }

    public void unregister(Semaphore wakeup) {
        wakeups.remove(wakeup);
    }

    /** The broker's store has grown: every registered consumer fetches again. */
    public void announce() {
        announce(Set.of());
    }

    /**
     * The broker's store has grown with events of these names (empty: unknown): the consumers that
     * handle one of them fetch again.
     */
    public void announce(java.util.Collection<String> eventNames) {
        for (var e : wakeups.entrySet()) {
            var w = e.getKey();
            var r = e.getValue();
            if (eventNames.isEmpty() || r.eventNames().isEmpty() || intersects(r.eventNames(), eventNames)) {
                wake(w);
            } else if (lazyMs > 0 && r.lateQueued().compareAndSet(false, true)) {
                lateWakeups().schedule(() -> {
                    r.lateQueued().set(false);
                    if (wakeups.get(w) == r) wake(w);
                }, lazyMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static void wake(Semaphore w) {
        if (w.availablePermits() == 0) w.release();
    }

    private java.util.concurrent.ScheduledExecutorService lateWakeups() {
        var s = lateWakeups;
        if (s == null) {
            synchronized (this) {
                s = lateWakeups;
                if (s == null) {
                    s = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                        var t = new Thread(r, "evento-head-late-wakeups");
                        t.setDaemon(true);
                        return t;
                    });
                    lateWakeups = s;
                }
            }
        }
        return s;
    }

    private static boolean intersects(Set<String> handled, java.util.Collection<String> appended) {
        for (var n : appended) if (handled.contains(n)) return true;
        return false;
    }

    /** The events {@code component} handles, from a consumer manager's event name → component → handler map. */
    public static Set<String> handledBy(java.util.Map<String, ? extends java.util.Map<String, ?>> handlers,
                                        String component) {
        var names = new java.util.HashSet<String>();
        handlers.forEach((event, byComponent) -> {
            if (byComponent.containsKey(component)) names.add(event);
        });
        return names;
    }

    /** The event names of an {@code evento:events-appended} body: an 8-byte head, then one name per line. */
    public static java.util.List<String> namesOf(byte[] body) {
        if (body == null || body.length <= Long.BYTES) return java.util.List.of();
        var text = new String(body, Long.BYTES, body.length - Long.BYTES, java.nio.charset.StandardCharsets.UTF_8);
        return java.util.List.of(text.split("\n"));
    }

    /**
     * The pause of a consumer after a fetch that found it at the head.
     *
     * @param wakeup     the engine's wake-up semaphore, registered here
     * @param pollingMs  the pause without notices (2.x: the room left in the batch, in ms)
     * @param fetchDelay the longest wait for a notice
     */
    public void pauseAtHead(Semaphore wakeup, long pollingMs, long fetchDelay) {
        await(wakeup, notified() ? Math.max(pollingMs, fetchDelay) : pollingMs);
    }

    /** Waits up to {@code millis} for a wake-up, then clears any other pending one. */
    public static void await(Semaphore wakeup, long millis) {
        if (millis <= 0) return;
        try {
            if (wakeup.tryAcquire(millis, TimeUnit.MILLISECONDS)) {
                wakeup.drainPermits();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
