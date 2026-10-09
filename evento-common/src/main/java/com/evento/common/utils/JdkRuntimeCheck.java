package com.evento.common.utils;

import java.util.Optional;

/**
 * Detects JDK builds with known defects that hurt Evento at runtime, so bundles and the server
 * can say so at startup instead of failing in ways that look like Evento bugs.
 *
 * <p>JDK 25.0.1 and 25.0.2 carry JDK-8370887: cancelling a timed park on a virtual thread can
 * break the order of the virtual-thread timer heap, and every pending timer then waits for a
 * later deadline. A {@code Thread.sleep(150)} was measured taking about 20 s. Bundle handlers,
 * consumer engines and the connection supervisor all run timed waits on virtual threads, so
 * the symptom is handler latency spikes, frozen projections and slow reconnects. 25.0.3 fixes it.
 */
public final class JdkRuntimeCheck {

    private JdkRuntimeCheck() {
    }

    /** The warning for the running JVM, if it is an affected build. */
    public static Optional<String> warning() {
        return warning(Runtime.version());
    }

    static Optional<String> warning(Runtime.Version version) {
        if (version.feature() == 25 && version.interim() == 0 && version.update() < 3) {
            return Optional.of(("event=jdk_virtual_thread_timer_bug jdk=%s: JDK 25.0.1/25.0.2 have "
                    + "JDK-8370887, which can stall virtual-thread timed waits (sleep, timed get, "
                    + "tryAcquire) for tens of seconds. Upgrade to JDK 25.0.3 or later.")
                    .formatted(version));
        }
        return Optional.empty();
    }
}
