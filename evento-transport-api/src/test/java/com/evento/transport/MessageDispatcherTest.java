package com.evento.transport;

import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Ping;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageDispatcherTest {

    record Ctx(String id) {}

    @Test
    void registeredHandlerIsInvoked() {
        var dispatcher = new MessageDispatcher<Ctx>();
        var captured = new AtomicReference<Hello>();
        dispatcher.register(Hello.class, (msg, ctx) -> captured.set(msg));

        var hello = new Hello(UUID.randomUUID(), (byte) 2, "b", "i", "v", Set.of(), 0L);
        dispatcher.dispatch(hello, new Ctx("ctx"));

        assertThat(captured.get()).isEqualTo(hello);
    }

    @Test
    void duplicateRegistrationRejected() {
        var dispatcher = new MessageDispatcher<Ctx>();
        dispatcher.register(Ping.class, (m, c) -> {});
        assertThatThrownBy(() -> dispatcher.register(Ping.class, (m, c) -> {}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fallbackInvokedForUnknownType() {
        var dispatcher = new MessageDispatcher<Ctx>();
        var fallbackCount = new AtomicInteger();
        dispatcher.onUnhandled((m, c) -> fallbackCount.incrementAndGet());

        dispatcher.dispatch(new Ping(UUID.randomUUID(), 1L, 0L), new Ctx("c"));
        assertThat(fallbackCount.get()).isEqualTo(1);
    }

    @Test
    void unhandledWithoutFallbackIsNoOp() {
        var dispatcher = new MessageDispatcher<Ctx>();
        // Should not throw.
        dispatcher.dispatch(new Ping(UUID.randomUUID(), 1L, 0L), new Ctx("c"));
    }

    @Test
    void handlerExceptionsPropagate() {
        var dispatcher = new MessageDispatcher<Ctx>();
        dispatcher.register(Ping.class, (m, c) -> { throw new RuntimeException("boom"); });
        assertThatThrownBy(() ->
                dispatcher.dispatch(new Ping(UUID.randomUUID(), 1L, 0L), new Ctx("c")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }

    @Test
    void dispatchOfNullMessageIsNoOp() {
        var dispatcher = new MessageDispatcher<Ctx>();
        dispatcher.dispatch((Message) null, new Ctx("c"));
    }
}
