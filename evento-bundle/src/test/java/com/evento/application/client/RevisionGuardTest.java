package com.evento.application.client;

import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.common.modeling.state.AggregateState;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.common.versioning.Revision;
import com.evento.transport.HandshakeProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevisionGuardTest {

    @Revision(2)
    public static class Revised extends DomainEvent {
    }

    public static class Unrevised extends DomainEvent {
    }

    @Revision(1)
    public static class RevisedState extends AggregateState {
    }

    @Revision(1)
    public static class RevisedQuery extends Query<Single<com.evento.common.modeling.messaging.payload.View>> {
    }

    private static final RevisionGuard OLD_BROKER = new RevisionGuard(() -> false);
    private static final RevisionGuard NEW_BROKER = new RevisionGuard(() -> true);

    @Test
    void anOldBrokerIsNotGivenARevisedEventToStore() {
        var reply = new DomainCommandResponseMessage(new DomainEventMessage(new Revised()), null, false);

        assertThatThrownBy(() -> OLD_BROKER.checkReply(reply))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Revised")
                .hasMessageContaining("revision 2")
                .hasMessageContaining(HandshakeProtocol.CAPABILITY_REVISION);
        assertThatCode(() -> NEW_BROKER.checkReply(reply)).doesNotThrowAnyException();
    }

    @Test
    void aBrokerWithoutEventListsIsNotGivenSeveralEvents() {
        var reply = new DomainCommandResponseMessage(new DomainEventMessage(new Unrevised()), null, false);
        reply.setPrecedingEventMessages(java.util.List.of(new DomainEventMessage(new Unrevised())));

        assertThatThrownBy(() -> new RevisionGuard(() -> true, () -> false).checkReply(reply))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 events")
                .hasMessageContaining(HandshakeProtocol.CAPABILITY_EVENTS);
        assertThatCode(() -> new RevisionGuard(() -> true, () -> true).checkReply(reply)).doesNotThrowAnyException();
        // One event needs nothing from the broker.
        assertThatCode(() -> new RevisionGuard(() -> true, () -> false).checkReply(
                new DomainCommandResponseMessage(new DomainEventMessage(new Unrevised()), null, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void theRevisionOfEveryEventIsChecked() {
        var reply = new DomainCommandResponseMessage(new DomainEventMessage(new Unrevised()), null, false);
        reply.setPrecedingEventMessages(java.util.List.of(new DomainEventMessage(new Revised())));

        assertThatThrownBy(() -> new RevisionGuard(() -> false, () -> true).checkReply(reply))
                .hasMessageContaining("revision 2");
    }

    @Test
    void anOldBrokerIsNotGivenARevisedSnapshot() {
        var reply = new DomainCommandResponseMessage(new DomainEventMessage(new Unrevised()),
                new SerializedAggregateState<>(new RevisedState()), false);

        assertThatThrownBy(() -> OLD_BROKER.checkReply(reply)).hasMessageContaining("RevisedState");
    }

    @Test
    void anOldBrokerIsNotSentARevisedRequest() {
        assertThatThrownBy(() -> OLD_BROKER.checkOutgoing(new QueryMessage<>(new RevisedQuery())))
                .hasMessageContaining("RevisedQuery");
    }

    @Test
    void unrevisedPayloadsGoToAnyBroker() {
        assertThatCode(() -> OLD_BROKER.checkReply(
                new DomainCommandResponseMessage(new DomainEventMessage(new Unrevised()), null, false)))
                .doesNotThrowAnyException();
        assertThatCode(() -> OLD_BROKER.checkOutgoing("not a message")).doesNotThrowAnyException();
    }
}
