package com.evento.server.es;

import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.protocol.ProtocolNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tells the bundles when the event store has grown, so that their consumers at the head wait for
 * it instead of polling ({@link HandshakeProtocol#CAPABILITY_HEAD}).
 *
 * <p>It reads the head from the database every {@code evento.es.head.poll.ms} (50 ms by default:
 * one indexed {@code max()} a tick, against the fetch a tick of every idle consumer it replaces)
 * and sends {@link ProtocolNotifications#EVENTS_APPENDED} to its sessions when the head moved,
 * with the names of the events appended since the last notice (one range scan of the primary key):
 * a bundle wakes only the consumers that handle one of them. Without the names every notice woke
 * every consumer, each of them fetched, and on a manufacturing-execution system (~45 consumers, two fetches served at a time)
 * those waves kept the projections behind under load. Over {@link #MAX_NAMED_RANGE} events the
 * notice carries no names and wakes every consumer.
 * Reading the database rather than hooking its own appends is what keeps it right in a cluster:
 * an event appended through another server wakes this server's bundles as well, and no server
 * calls another. Under APES an event becomes fetchable only after the fetch delay, so the notice
 * goes out that much later.
 *
 * <p>0 or less disables it; the server then does not offer the capability (see
 * {@link #enabled(long)}) and the bundles poll as before.
 */
@Component
public class HeadNotifier implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HeadNotifier.class);

    /** Above this many new events a notice names none: reading their names would cost more than waking everyone. */
    static final long MAX_NAMED_RANGE = 10_000;

    /** What a notice says: the new head and the names of the events up to it (empty: any). */
    record Notice(long head, java.util.List<String> eventNames) {
        byte[] encode() {
            var names = String.join("\n", eventNames).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return ByteBuffer.allocate(Long.BYTES + names.length).putLong(head).put(names).array();
        }
    }

    private final java.util.function.LongSupplier headReader;
    private final java.util.function.BiFunction<Long, Long, java.util.List<String>> namesReader;
    private final java.util.function.LongSupplier visibilityDelay;
    private final java.util.function.Consumer<Notice> announcer;
    private final ScheduledExecutorService ticker;
    private long lastHead = -1;
    private long failingSince;

    @org.springframework.beans.factory.annotation.Autowired
    public HeadNotifier(EventStore eventStore, BusLifecycle bus,
                        @Value("${evento.es.head.poll.ms:50}") long pollMs) {
        this(eventStore::getLastEventSequenceNumber, eventStore::eventNamesBetween, eventStore::visibilityDelayMs,
                notice -> bus.broadcast(HandshakeProtocol.CAPABILITY_HEAD, ProtocolNotifications.EVENTS_APPENDED,
                        notice.encode()),
                pollMs);
    }

    /** @param pollMs 0 or less: no ticker ({@link #tick()} only when called, for tests) */
    HeadNotifier(java.util.function.LongSupplier headReader,
                 java.util.function.BiFunction<Long, Long, java.util.List<String>> namesReader,
                 java.util.function.LongSupplier visibilityDelay,
                 java.util.function.Consumer<Notice> announcer, long pollMs) {
        this.headReader = headReader;
        this.namesReader = namesReader;
        this.visibilityDelay = visibilityDelay;
        this.announcer = announcer;
        if (!enabled(pollMs)) {
            this.ticker = null;
            log.info("event=head_notifier_disabled - consumers poll the event store");
            return;
        }
        this.ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "evento-head-notifier");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleWithFixedDelay(this::tick, pollMs, pollMs, TimeUnit.MILLISECONDS);
        log.info("event=head_notifier_started pollMs={}", pollMs);
    }

    /** Whether a poll interval turns the notifier on (and the capability with it). */
    public static boolean enabled(long pollMs) {
        return pollMs > 0;
    }

    void tick() {
        long head;
        try {
            head = headReader.getAsLong();
            failingSince = 0;
        } catch (RuntimeException e) {
            // The database is away: the consumers' own fetch delay covers the gap.
            if (failingSince == 0) {
                failingSince = System.currentTimeMillis();
                log.warn("event=head_read_failed cause={}", e.toString());
            }
            return;
        }
        if (head <= lastHead) return;
        long previous = lastHead;
        lastHead = head;
        if (previous < 0) return;  // nothing new since the server started: no one is behind because of it
        java.util.List<String> names = java.util.List.of();
        if (head - previous <= MAX_NAMED_RANGE) {
            try {
                names = namesReader.apply(previous, head);
            } catch (RuntimeException e) {
                log.debug("event=head_names_unread cause={} - the notice wakes every consumer", e.toString());
            }
        }
        var notice = new Notice(head, names);
        long delay = visibilityDelay.getAsLong();
        if (delay > 0 && ticker != null) {
            ticker.schedule(() -> announce(notice), delay, TimeUnit.MILLISECONDS);
        } else {
            announce(notice);
        }
    }

    void announce(Notice notice) {
        try {
            announcer.accept(notice);
        } catch (RuntimeException e) {
            log.debug("event=head_announce_failed cause={}", e.toString());
        }
    }

    @Override
    public void close() {
        if (ticker != null) ticker.shutdownNow();
    }
}
