package com.evento.common.utils;

/**
 * Escalation policy for {@link VirtualMachineError}s (OutOfMemoryError, StackOverflowError,
 * InternalError) that reach a catch-all {@code catch (Throwable)} handler.
 *
 * <p>Catch-all handlers exist so one failing subscriber, handler or connection never takes
 * down its siblings — but that isolation is only sound for application-level failures. A
 * {@code VirtualMachineError} means the JVM itself is compromised: an OutOfMemoryError that
 * has already fired may have killed arbitrary other threads (Netty event loops, executors)
 * mid-operation, leaving the process alive but unable to serve. Production incident
 * 2026-07-27: an OOM during event-fetch handling killed the broker's Netty worker event
 * loops; every catch-all swallowed the error, the JVM survived as a zombie that
 * force-closed all incoming connections for three hours, and the container restart policy
 * never fired because the process never exited.
 *
 * <p>Policy: when a fatal error is detected anywhere in the cause chain, halt the JVM with
 * exit code 3 (the same code {@code -XX:+ExitOnOutOfMemoryError} uses) so a supervisor
 * (Docker, systemd, Kubernetes) can restart a clean process. {@link Runtime#halt(int)} is
 * used instead of {@code System.exit} because shutdown hooks cannot be trusted to complete
 * in a memory-exhausted JVM.
 *
 * <p>The JVM flag {@code -XX:+ExitOnOutOfMemoryError} remains the first line of defense
 * (it also covers threads whose infrastructure swallows Throwables internally, e.g. Netty
 * event loops); this class is the in-process backstop for deployments launched without it.
 *
 * <p>Halting can be disabled for tests with {@code -Devento.fatal.halt=false}.
 */
public final class FatalErrors {

    /** System property gating the halt behavior; defaults to enabled. */
    public static final String HALT_PROPERTY = "evento.fatal.halt";

    /** Exit code used on halt; matches the JVM's own {@code ExitOnOutOfMemoryError}. */
    public static final int EXIT_CODE = 3;

    /** Bound on cause-chain traversal (defends against cyclic cause chains). */
    private static final int MAX_CAUSE_DEPTH = 16;

    private FatalErrors() {
    }

    /**
     * Returns true when {@code t} or any of its (bounded) cause chain is a
     * {@link VirtualMachineError}. Wrapping matters: an OOM thrown inside a repository
     * call typically surfaces as {@code RuntimeException(PersistenceException(OOM))}.
     */
    public static boolean isFatal(Throwable t) {
        int depth = 0;
        for (Throwable cur = t; cur != null && depth < MAX_CAUSE_DEPTH; cur = cur.getCause(), depth++) {
            if (cur instanceof VirtualMachineError) return true;
        }
        return false;
    }

    /**
     * Halts the JVM if {@code t} is fatal (see {@link #isFatal}); otherwise returns.
     * Call this FIRST in a {@code catch (Throwable)} block, before any logging — log
     * frameworks allocate, and in a memory-exhausted JVM that allocation can itself
     * throw and skip the escalation.
     */
    public static void escalateIfFatal(Throwable t) {
        if (!isFatal(t)) return;
        if (!Boolean.parseBoolean(System.getProperty(HALT_PROPERTY, "true"))) return;
        try {
            // System.err, not a logging framework: minimal allocation, no appender state.
            System.err.println("FATAL: VirtualMachineError reached a catch-all handler; halting JVM (exit "
                    + EXIT_CODE + "): " + t);
            t.printStackTrace(System.err);
        } catch (Throwable ignored) {
            // Reporting is best-effort; the halt below is the point.
        }
        Runtime.getRuntime().halt(EXIT_CODE);
    }
}
