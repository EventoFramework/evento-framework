package com.evento.application.proxy;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.SagaReference;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.common.modeling.state.SagaState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Keys derived for commands sent from consumer handlers (G5): a redelivered event, or the
 * handler's own retry, must re-issue exactly the keys of the first attempt.
 */
class DerivedIdempotencyKeyTest {

    /** Records the key each command was sent with; answers every command with {@code null}. */
    static final class RecordingGateway implements CommandGateway {
        final List<String> keys = new CopyOnWriteArrayList<>();

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit) {
            return send(command, metadata, handledMessage, timeout, unit, null);
        }

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit, String idempotencyKey) {
            keys.add(String.valueOf(idempotencyKey));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean supportsIdempotencyKeys() {
            return true;
        }
    }

    /** A gateway decorator written before keys: only the five-argument send. */
    static final class LegacyDecorator implements CommandGateway {
        final List<String> sent = new CopyOnWriteArrayList<>();

        @Override
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit) {
            sent.add(command.getClass().getSimpleName());
            return CompletableFuture.completedFuture(null);
        }
    }

    public static class OtherCmd extends ServiceCommand {
    }

    public static class Cmd extends ServiceCommand {
    }

    public static class OrderCompleted extends DomainEvent {
        public String orderId = "o-1";
    }

    public static class State extends SagaState {
    }

    private static GatewayTelemetryProxy proxy(CommandGateway target) {
        return new GatewayTelemetryProxy(target, null, "saga-bundle", null, "PaymentSaga",
                new DomainEventMessage(new OrderCompleted()), new TracingAgent("saga-bundle", 1), "saga-1");
    }

    @Test
    void aScopedProxyDerivesOneKeyPerCommandFromConsumerEventAndOrdinal() {
        var target = new RecordingGateway();
        var gw = proxy(target).withDerivedIdempotencyKeys("saga-bundle_PaymentSaga_1_EUROPA", 42);
        gw.send(new Cmd());
        gw.send(new Cmd(), 5, TimeUnit.SECONDS);
        gw.sendIdempotent(new Cmd(), "caller-chosen");
        gw.send(new Cmd());
        assertThat(target.keys).containsExactly(
                "evento:saga-bundle_PaymentSaga_1_EUROPA:42:Cmd:0",
                "evento:saga-bundle_PaymentSaga_1_EUROPA:42:Cmd:1",
                "caller-chosen",
                // an explicit key takes its ordinal too, so it does not shift the others
                "evento:saga-bundle_PaymentSaga_1_EUROPA:42:Cmd:3");

        // The same event redelivered to a fresh proxy: the same keys.
        var redelivered = new RecordingGateway();
        var again = proxy(redelivered).withDerivedIdempotencyKeys("saga-bundle_PaymentSaga_1_EUROPA", 42);
        again.send(new Cmd());
        again.send(new Cmd());
        assertThat(redelivered.keys).containsExactlyElementsOf(target.keys.subList(0, 2));
    }

    @Test
    void anUnscopedProxySendsWithoutAKey() {
        var target = new RecordingGateway();
        proxy(target).send(new Cmd());
        assertThat(target.keys).containsExactly("null");
    }

    @Test
    void anOverlongConsumerIdIsHashedToAStableKeyWithinTheColumn() {
        var scope = "c".repeat(400) + ":7";
        var key = GatewayTelemetryProxy.deriveIdempotencyKey(scope, "Cmd", 0);
        assertThat(key).startsWith("evento:sha256:").hasSizeLessThanOrEqualTo(255);
        assertThat(GatewayTelemetryProxy.deriveIdempotencyKey(scope, "Cmd", 0)).isEqualTo(key);
        assertThat(GatewayTelemetryProxy.deriveIdempotencyKey(scope, "Cmd", 1)).isNotEqualTo(key);
        assertThat(GatewayTelemetryProxy.deriveIdempotencyKey(scope, "OtherCmd", 0)).isNotEqualTo(key);
    }

    /** A saga whose handler sends two commands and fails the first time, after sending them. */
    public static class FlakySaga {
        static final AtomicInteger attempts = new AtomicInteger();

        @SagaEventHandler(init = true, associationProperty = "orderId", retry = 3, retryDelay = 0)
        public State on(OrderCompleted event, CommandGateway gateway) throws Exception {
            gateway.send(new Cmd()).get();
            gateway.send(new Cmd()).get();
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("transient failure");
            return new State();
        }
    }

    @Test
    void theSagaRetryLoopReissuesTheSameKeys() throws Throwable {
        FlakySaga.attempts.set(0);
        var target = new RecordingGateway();
        var gw = proxy(target).withDerivedIdempotencyKeys("saga-bundle_FlakySaga_1_EUROPA", 7);
        var event = new PublishedEvent();
        event.setEventSequenceNumber(7L);
        event.setEventName("OrderCompleted");
        event.setEventMessage(new DomainEventMessage(new OrderCompleted()));

        new SagaReference(new FlakySaga()).invoke(event, null, gw, null,
                new MessageHandlerInterceptor() {
                }, t -> {
                });

        assertThat(FlakySaga.attempts).hasValue(2);
        assertThat(target.keys).containsExactly(
                "evento:saga-bundle_FlakySaga_1_EUROPA:7:Cmd:0",
                "evento:saga-bundle_FlakySaga_1_EUROPA:7:Cmd:1",
                "evento:saga-bundle_FlakySaga_1_EUROPA:7:Cmd:0",
                "evento:saga-bundle_FlakySaga_1_EUROPA:7:Cmd:1");
    }

    @Test
    void ordinalsAreCountedPerCommandTypeSoASkippedCommandCannotHandItsKeyToAnotherType() {
        // Attempt 1 pays, then ships; attempt 2 skips the payment (a query said it was done).
        // With one ordinal for all commands the shipment of attempt 2 would get the payment's
        // key and be answered with the payment's event (HIGH-1). Per type, it keeps its own.
        var first = new RecordingGateway();
        var a1 = proxy(first).withDerivedIdempotencyKeys("saga", 9);
        a1.send(new Cmd());
        a1.send(new OtherCmd());
        a1.send(new Cmd());
        assertThat(first.keys).containsExactly("evento:saga:9:Cmd:0", "evento:saga:9:OtherCmd:0", "evento:saga:9:Cmd:1");

        var second = new RecordingGateway();
        var a2 = proxy(second).withDerivedIdempotencyKeys("saga", 9);
        a2.send(new OtherCmd());
        assertThat(second.keys).containsExactly("evento:saga:9:OtherCmd:0");
    }

    @Test
    void aGatewayThatCannotCarryKeysGetsThePlainSendInsteadOfFailingEveryCommand() throws Exception {
        // HIGH-3: a pre-G5 decorator must keep working behind a consumer handler's gateway.
        var legacy = new LegacyDecorator();
        var gw = proxy(legacy).withDerivedIdempotencyKeys("saga", 1);
        gw.send(new Cmd()).get(1, TimeUnit.SECONDS);
        gw.send(new OtherCmd(), 5, TimeUnit.SECONDS).get(1, TimeUnit.SECONDS);
        assertThat(legacy.sent).containsExactly("Cmd", "OtherCmd");
        assertThat(gw.supportsIdempotencyKeys()).isFalse();

        // An explicit key is never dropped silently: it still fails loudly.
        assertThatThrownBy(() -> gw.sendIdempotent(new Cmd(), "k-1").get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aCommandGatewayImplSubclassThatOnlyDecoratesTheFiveArgumentSendIsNotBypassed() throws Exception {
        var decorated = new java.util.concurrent.atomic.AtomicInteger();
        var sub = new com.evento.common.messaging.gateway.CommandGatewayImpl(new NullServer()) {
            @Override
            public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                                 long timeout, TimeUnit unit) {
                decorated.incrementAndGet();
                return super.send(command, metadata, handledMessage, timeout, unit);
            }
        };
        var gw = proxy(sub).withDerivedIdempotencyKeys("saga", 1);
        gw.send(new Cmd()).get(1, TimeUnit.SECONDS);
        assertThat(decorated).as("the override ran (no derived key, no bypass)").hasValue(1);
    }

    /** An EventoServer answering every request with {@code null}. */
    static final class NullServer implements com.evento.common.messaging.bus.EventoServer {
        @Override
        public void send(java.io.Serializable message) {
        }

        @Override
        public <T extends java.io.Serializable> CompletableFuture<T> request(java.io.Serializable request,
                                                                          long timeout, TimeUnit unit) {
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

    @Test
    void anExplicitKeyInTheReservedNamespaceIsRefused() {
        var target = new RecordingGateway();
        var gw = proxy(target).withDerivedIdempotencyKeys("saga", 1);
        assertThatThrownBy(() -> gw.sendIdempotent(new Cmd(), "evento:saga:1:Cmd:0").get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(target.keys).isEmpty();
    }
}
