package com.evento.transport.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static com.evento.transport.state.ConnectionState.*;

/**
 * Atomic state machine for a Transport connection.
 * Transitions are validated against an explicit graph; illegal attempts are
 * rejected with a logged warning rather than thrown, so reconnect loops can
 * make forward progress under race conditions.
 *
 * Listeners are notified on a CopyOnWriteArrayList — safe to iterate while
 * other threads register/deregister.
 */
public final class ConnectionStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ConnectionStateMachine.class);

    private final String connectionId;
    private final AtomicReference<ConnectionState> state;
    private final CopyOnWriteArrayList<BiConsumer<ConnectionState, ConnectionState>> listeners = new CopyOnWriteArrayList<>();

    public ConnectionStateMachine(String connectionId) {
        this(connectionId, DISCONNECTED);
    }

    public ConnectionStateMachine(String connectionId, ConnectionState initial) {
        this.connectionId = connectionId;
        this.state = new AtomicReference<>(initial);
    }

    public ConnectionState current() {
        return state.get();
    }

    public boolean is(ConnectionState s) {
        return state.get() == s;
    }

    public void addListener(BiConsumer<ConnectionState, ConnectionState> listener) {
        listeners.add(listener);
    }

    public void removeListener(BiConsumer<ConnectionState, ConnectionState> listener) {
        listeners.remove(listener);
    }

    /**
     * Attempt to move from {@code expected} to {@code next}. Returns true on success.
     * If the current state is not {@code expected}, the call fails silently with a debug log
     * and no listener notification.
     */
    public boolean compareAndTransition(ConnectionState expected, ConnectionState next, String reason) {
        if (!isLegalTransition(expected, next)) {
            log.warn("event=illegal_transition connection={} from={} to={} reason={}",
                    connectionId, expected, next, reason);
            return false;
        }
        if (!state.compareAndSet(expected, next)) {
            log.debug("event=transition_skipped connection={} expected={} actual={} target={} reason={}",
                    connectionId, expected, state.get(), next, reason);
            return false;
        }
        log.info("event=state_transition connection={} from={} to={} reason={}",
                connectionId, expected, next, reason);
        notifyListeners(expected, next);
        return true;
    }

    /**
     * Force a transition regardless of the current state (e.g. for hard close on shutdown).
     * Still validates against the legal graph; use cautiously.
     */
    public ConnectionState forceTransition(ConnectionState next, String reason) {
        ConnectionState previous = state.getAndSet(next);
        if (previous == next) {
            return previous;
        }
        log.info("event=state_forced connection={} from={} to={} reason={}",
                connectionId, previous, next, reason);
        notifyListeners(previous, next);
        return previous;
    }

    private void notifyListeners(ConnectionState from, ConnectionState to) {
        for (BiConsumer<ConnectionState, ConnectionState> listener : listeners) {
            try {
                listener.accept(from, to);
            } catch (Throwable t) {
                log.error("event=listener_error connection={} from={} to={}", connectionId, from, to, t);
            }
        }
    }

    private static boolean isLegalTransition(ConnectionState from, ConnectionState to) {
        if (from == to) return false;
        return LEGAL_TARGETS.getOrDefault(from, Set.of()).contains(to);
    }

    private static final java.util.Map<ConnectionState, Set<ConnectionState>> LEGAL_TARGETS = java.util.Map.of(
            DISCONNECTED, Set.of(CONNECTING, CLOSING, CLOSED),
            CONNECTING,   Set.of(HANDSHAKING, DISCONNECTED, CLOSING, CLOSED),
            HANDSHAKING,  Set.of(CONNECTED, DISCONNECTED, CLOSING, CLOSED),
            CONNECTED,    Set.of(DEGRADED, DISCONNECTED, CLOSING, CLOSED),
            DEGRADED,     Set.of(CONNECTED, DISCONNECTED, CLOSING, CLOSED),
            CLOSING,      Set.of(CLOSED),
            CLOSED,       Set.of()
    );
}
