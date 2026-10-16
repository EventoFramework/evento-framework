package com.evento.application.consumer;

import com.evento.common.modeling.messaging.query.ConsistencyToken;

import java.util.function.Supplier;

/**
 * Read-your-writes over this bundle's projectors: a query waits until every projector
 * whose context the token names (every projector, for the wildcard context) has processed
 * the token's position for it. A projector of another context is not concerned: it never
 * sees those events, and the read model it feeds does not hold them.
 *
 * <p>The projectors are checked in turn against the same deadline, so the wait is that of
 * the slowest one. A bundle with no projector (its projections read a store fed elsewhere)
 * cannot tell, and does not wait.
 */
public final class ProjectorConsistencyGate implements ConsistencyGate {

    private final Supplier<EngineSupervisor> supervisor;

    /** @param supervisor the bundle's engines; null while they are not started */
    public ProjectorConsistencyGate(Supplier<EngineSupervisor> supervisor) {
        this.supervisor = supervisor;
    }

    @Override
    public long await(ConsistencyToken token, long deadlineNanos) throws InterruptedException {
        if (token == null || token.isEmpty()) return 0L;
        var engines = supervisor.get();
        if (engines == null) return 0L;
        long waited = 0L;
        for (var projector : engines.getProjectorEngines()) {
            long position = token.requiredFor(projector.getContext());
            if (position < 0) continue;
            waited += projector.awaitPosition(position, deadlineNanos);
        }
        return waited;
    }
}
