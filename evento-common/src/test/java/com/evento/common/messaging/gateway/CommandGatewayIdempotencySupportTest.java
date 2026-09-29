package com.evento.common.messaging.gateway;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which gateways may be handed a derived idempotency key (G5 review, HIGH-3), and the
 * caller-key rules (LOW-4).
 */
class CommandGatewayIdempotencySupportTest {

    public static class Cmd extends ServiceCommand {
    }

    /** Records every message sent; answers {@code null}. */
    static final class RecordingServer implements EventoServer {
        final List<CommandMessage<?>> sent = new ArrayList<>();

        @Override
        public void send(Serializable message) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Serializable> CompletableFuture<T> request(Serializable request, long timeout, TimeUnit unit) {
            sent.add((CommandMessage<?>) request);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String getInstanceId() {
            return "i";
        }

        @Override
        public String getBundleId() {
            return "b";
        }
    }

    /** A pre-G5 subclass: decorates the five-argument send only. */
    static class LegacySubclass extends CommandGatewayImpl {
        int decorated;

        LegacySubclass(EventoServer server) {
            super(server);
        }

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit) {
            decorated++;
            return super.send(command, metadata, handledMessage, timeout, unit);
        }
    }

    /** A subclass that decorates both sends consistently. */
    static class KeyAwareSubclass extends LegacySubclass {
        KeyAwareSubclass(EventoServer server) {
            super(server);
        }

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit, String idempotencyKey) {
            decorated++;
            return super.send(command, metadata, handledMessage, timeout, unit, idempotencyKey);
        }
    }

    /** A decorator written against the pre-G5 interface. */
    static final class LegacyDecorator implements CommandGateway {
        final CommandGateway target;

        LegacyDecorator(CommandGateway target) {
            this.target = target;
        }

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit) {
            return target.send(command, metadata, handledMessage, timeout, unit);
        }
    }

    @Test
    void onlyAGatewayWhoseKeyedSendIsNotBypassedSupportsKeys() {
        var server = new RecordingServer();
        assertThat(new CommandGatewayImpl(server).supportsIdempotencyKeys()).isTrue();
        assertThat(new KeyAwareSubclass(server).supportsIdempotencyKeys()).isTrue();
        assertThat(new LegacySubclass(server).supportsIdempotencyKeys())
                .as("a keyed send would skip the five-argument override").isFalse();
        assertThat(new LegacyDecorator(new CommandGatewayImpl(server)).supportsIdempotencyKeys()).isFalse();
    }

    @Test
    void anExplicitKeyOnAGatewayThatCannotCarryItStillFailsLoudly() {
        var decorator = new LegacyDecorator(new CommandGatewayImpl(new RecordingServer()));
        assertThatThrownBy(() -> decorator.sendIdempotent(new Cmd(), "k-1").get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aCallerChosenKeyMayNotUseTheReservedPrefix() throws Exception {
        var server = new RecordingServer();
        var gateway = new CommandGatewayImpl(server);
        assertThatThrownBy(() -> gateway.sendIdempotent(new Cmd(), "evento:consumer:1:Cmd:0").get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        assertThatThrownBy(() -> gateway.sendIdempotent(new Cmd(), "k".repeat(256)).get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(server.sent).isEmpty();

        gateway.sendIdempotent(new Cmd(), "order-42").get(1, TimeUnit.SECONDS);
        assertThat(server.sent).singleElement().extracting(CommandMessage::getIdempotencyKey).isEqualTo("order-42");
    }
}
