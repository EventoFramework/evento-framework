package com.evento.server.es;

import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.transport.RequestExpiredException;

import java.util.function.Supplier;

/**
 * The deadline of the aggregate command whose event is being appended on this thread (N15), on
 * {@link BusLifecycle#monotonicMillis()}: the store waits for {@code es-lock} no longer than what is
 * left of it, so an event is not stored after its caller was told the command timed out. Only
 * {@code CommandBrokerHandler} sets it, around an aggregate append; a service command's event
 * (whose handler has run, with its side effects) is stored whatever the time, so it has none.
 */
public final class AppendDeadline {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private AppendDeadline() {
    }

    /** Runs {@code append} with {@code deadlineMs} as the current deadline. */
    public static <T> T within(long deadlineMs, Supplier<T> append) {
        Long previous = CURRENT.get();
        CURRENT.set(deadlineMs);
        try {
            return append.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** The current deadline, {@link Long#MAX_VALUE} when there is none. */
    static long current() {
        Long deadline = CURRENT.get();
        return deadline == null ? Long.MAX_VALUE : deadline;
    }

    /**
     * The {@code lock_timeout} for {@code es-lock}: what is left of {@code deadlineMs} (at least 1 ms,
     * since 0 would mean no timeout), or {@code "0"} (none) without a deadline.
     */
    static String lockTimeout(long deadlineMs) {
        if (deadlineMs == Long.MAX_VALUE) return "0";
        return Math.max(1, deadlineMs - BusLifecycle.monotonicMillis()) + "ms";
    }

    static boolean passed(long deadlineMs) {
        return deadlineMs != Long.MAX_VALUE && BusLifecycle.monotonicMillis() >= deadlineMs;
    }

    static RequestExpiredException expired(String where) {
        return new RequestExpiredException("the command's deadline passed " + where + "; its event was not stored");
    }

    /** Whether {@code e} (or a cause) is PostgreSQL's lock_not_available: the lock_timeout ran out. */
    static boolean lockTimedOut(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) return true;
        }
        return false;
    }
}
