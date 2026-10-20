package com.evento.application.client;

import com.evento.common.modeling.common.SerializedObject;
import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.versioning.Revisions;
import com.evento.transport.HandshakeProtocol;

import java.util.function.BooleanSupplier;

/**
 * Refuses to hand a payload of a revised class to a broker that would drop its revision (3.0),
 * and several events of one command to a broker that would store only the last one (3.0, F9).
 *
 * <p>A broker older than 3.0 decodes the envelope of a command, a query or a stored event with
 * classes that have no revision, and encodes it again without one. A payload written at revision
 * 2 would be stored as revision 0, and every later read would upcast it from 0: the upcasters
 * would run on a shape they were not written for, silently. Failing the one message that would
 * be corrupted, with the reason, is the only safe answer; the cure is upgrading the brokers before
 * the bundles that send revised payloads, which is the usual order anyway.
 */
public final class RevisionGuard {

    private final BooleanSupplier brokerKeepsRevisions;
    private final BooleanSupplier brokerStoresSeveralEvents;

    /** @param brokerKeepsRevisions whether the current session negotiated {@link HandshakeProtocol#CAPABILITY_REVISION} */
    public RevisionGuard(BooleanSupplier brokerKeepsRevisions) {
        this(brokerKeepsRevisions, () -> true);
    }

    /**
     * @param brokerKeepsRevisions      whether the current session negotiated {@link HandshakeProtocol#CAPABILITY_REVISION}
     * @param brokerStoresSeveralEvents whether it negotiated {@link HandshakeProtocol#CAPABILITY_EVENTS}
     */
    public RevisionGuard(BooleanSupplier brokerKeepsRevisions, BooleanSupplier brokerStoresSeveralEvents) {
        this.brokerKeepsRevisions = brokerKeepsRevisions;
        this.brokerStoresSeveralEvents = brokerStoresSeveralEvents;
    }

    /** No check: every payload may be sent (tests, in-process use). */
    public static final RevisionGuard NONE = new RevisionGuard(() -> true, () -> true);

    /** Checks a request this bundle sends: a command or a query. */
    public void checkOutgoing(Object request) {
        if (request instanceof Message<?> m) check(m.getSerializedPayload(), "send");
    }

    /** Checks a reply this bundle's handler produced: the event it asks the broker to store, and the snapshot. */
    public void checkReply(Object reply) {
        if (reply instanceof DomainCommandResponseMessage r) {
            if (r.getPrecedingEventMessages() != null && !brokerStoresSeveralEvents.getAsBoolean()) {
                throw new IllegalStateException("cannot store the " + r.allEventMessages().size() + " events of one "
                        + "command: the broker of this session stores one event per command (no "
                        + HandshakeProtocol.CAPABILITY_EVENTS + ", older than 3.0) and would drop all but the last. "
                        + "Upgrade the brokers to 3.0 before deploying handlers that return DomainEvents.");
            }
            for (var event : r.allEventMessages()) check(event.getSerializedPayload(), "store");
            check(r.getSerializedAggregateState(), "store");
        } else if (reply instanceof Message<?> m) {
            check(m.getSerializedPayload(), "store");
        }
    }

    private void check(SerializedObject<?> payload, String action) {
        if (payload == null || payload.getRevision() == 0 || brokerKeepsRevisions.getAsBoolean()) return;
        throw new IllegalStateException("cannot " + action + " " + Revisions.payloadName(payload.getObjectClass())
                + " (revision " + payload.getRevision() + "): the broker of this session does not keep payload "
                + "revisions (no " + HandshakeProtocol.CAPABILITY_REVISION + ", older than 3.0) and would record it "
                + "as revision 0. Upgrade the brokers to 3.0 before deploying revised payload classes.");
    }
}
