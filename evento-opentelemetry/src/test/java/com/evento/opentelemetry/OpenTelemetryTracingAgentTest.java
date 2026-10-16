package com.evento.opentelemetry;

import com.evento.application.performance.TracingAgent;
import com.evento.application.proxy.CorrelatingGateways;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.modeling.messaging.query.Single;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenTelemetryTracingAgentTest {

    public static class PlaceOrder extends DomainCommand {
        String orderId = "o1";
        @Override public String getAggregateId() { return orderId; }
    }

    public static class OrderPlaced extends DomainEvent {
        String orderId = "o1";
    }

    public static class OrderView implements View {
    }

    public static class FindOrder extends Query<Single<OrderView>> {
    }

    /** Records what is sent and answers with {@link #reply}, which a test completes when it wants. */
    static final class RecordingGateway implements CommandGateway, QueryGateway {
        final List<Metadata> sent = new ArrayList<>();
        CompletableFuture<Object> reply = new CompletableFuture<>();

        @Override
        @SuppressWarnings("unchecked")
        public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                             long timeout, TimeUnit unit) {
            sent.add(metadata);
            return (CompletableFuture<R>) reply;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata,
                                                                      Message<?> handledMessage, long timeout,
                                                                      TimeUnit unit) {
            sent.add(metadata);
            return (CompletableFuture<T>) (CompletableFuture<?>) reply;
        }
    }

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private OpenTelemetrySdk sdk;
    private OpenTelemetryTracingAgent agent;

    @BeforeEach
    void setUp() {
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        agent = new OpenTelemetryTracingAgent("orders", 3, sdk);
    }

    @AfterEach
    void tearDown() {
        sdk.close();
    }

    @Test
    void aHandlerRunsInASpanNamedAfterComponentAndPayload() throws Throwable {
        var result = agent.track(new DomainCommandMessage(new PlaceOrder()), "OrderAggregate", null, () -> "done");

        assertThat(result).isEqualTo("done");
        var span = single();
        // The payload as the framework names it (the class name after the last dot).
        var payload = new DomainCommandMessage(new PlaceOrder()).getPayloadName();
        assertThat(span.getName()).isEqualTo("OrderAggregate " + payload);
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getAttributes().asMap()).containsValues("orders", "OrderAggregate", payload, "evento");
        assertThat(span.getInstrumentationScopeInfo().getName()).isEqualTo(OpenTelemetryTracingAgent.SCOPE);
    }

    @Test
    void aFailingHandlerRecordsTheErrorAndRethrows() {
        assertThatThrownBy(() -> agent.track(new DomainCommandMessage(new PlaceOrder()), "OrderAggregate", null, () -> {
            throw new IllegalStateException("over budget");
        })).isInstanceOf(IllegalStateException.class);

        var span = single();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anyMatch(e -> e.getName().equals("exception"));
    }

    @Test
    void whatAHandlerSendsCarriesItsSpanAndTheReceiverJoinsTheTrace() throws Throwable {
        var event = new DomainEventMessage(new OrderPlaced());
        agent.track(new DomainCommandMessage(new PlaceOrder()), "OrderAggregate", null, () -> {
            agent.correlate(new DomainCommandMessage(new PlaceOrder()), event);
            return null;
        });
        var producer = single();
        assertThat(event.getMetadata().get("traceparent")).contains(producer.getSpanId());

        exporter.reset();
        agent.track(event, "OrderProjector", null, () -> null);
        var consumer = single();
        assertThat(consumer.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(consumer.getTraceId()).isEqualTo(producer.getTraceId());
        assertThat(consumer.getParentSpanId()).isEqualTo(producer.getSpanId());
    }

    @Test
    void linkingConsumersStartANewTraceLinkedToTheProducer() throws Throwable {
        var linking = agent.linkingConsumers();
        var event = new DomainEventMessage(new OrderPlaced());
        linking.track(new DomainCommandMessage(new PlaceOrder()), "OrderAggregate", null, () -> {
            linking.correlate(new DomainCommandMessage(new PlaceOrder()), event);
            return null;
        });
        var producer = single();
        exporter.reset();

        linking.track(event, "OrderProjector", null, () -> null);
        var consumer = single();
        assertThat(consumer.getTraceId()).isNotEqualTo(producer.getTraceId());
        assertThat(consumer.getLinks()).singleElement()
                .satisfies(l -> assertThat(l.getSpanContext().getSpanId()).isEqualTo(producer.getSpanId()));
    }

    @Test
    void withoutASpanTheHandledMessageCarriesTheTraceOn() {
        var handled = new DomainCommandMessage(new PlaceOrder());
        var meta = new Metadata();
        meta.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        handled.setMetadata(meta);

        var out = agent.correlate((Metadata) null, handled);

        assertThat(out.get("traceparent")).isEqualTo("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
    }

    @Test
    void withNothingToPropagateTheMetadataIsReturnedAsIs() {
        var meta = new Metadata();
        meta.put("tenant", "acme");
        assertThat(agent.correlate(meta, null)).isSameAs(meta);
        assertThat(agent.correlate((Metadata) null, null)).isNull();
    }

    @Test
    void theCallersMetadataIsNotModified() throws Throwable {
        var meta = new Metadata();
        meta.put("tenant", "acme");
        agent.track(new DomainCommandMessage(new PlaceOrder()), "Api", null, () -> {
            var out = agent.correlate(meta, null);
            assertThat(out).containsEntry("tenant", "acme").containsKey("traceparent");
            return null;
        });
        assertThat(meta).containsOnlyKeys("tenant");
    }

    @Test
    void applicationGatewaysAreCorrelatedOnlyWithARealAgent() throws Throwable {
        var sent = new ArrayList<Metadata>();
        CommandGateway raw = new CommandGateway() {
            @Override
            public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                                 long timeout, TimeUnit unit) {
                sent.add(metadata);
                return CompletableFuture.completedFuture(null);
            }
        };
        assertThat(CorrelatingGateways.commands(raw, new TracingAgent("orders", 1))).isSameAs(raw);

        var gateway = CorrelatingGateways.commands(raw, agent);
        agent.track(new DomainCommandMessage(new PlaceOrder()), "HttpRequest", null, () -> gateway.send(new PlaceOrder()));
        assertThat(sent).singleElement().satisfies(m -> assertThat(m).containsKey("traceparent"));
    }

    @Test
    void aSendRunsInAClientSpanUntilTheReply() throws Throwable {
        var raw = new RecordingGateway();
        var gateway = CorrelatingGateways.commands(raw, agent);
        var pending = new ArrayList<CompletableFuture<Object>>();
        agent.track(new DomainCommandMessage(new PlaceOrder()), "HttpRequest", null,
                () -> pending.add(gateway.send(new PlaceOrder())));
        var handler = single();
        assertThat(pending.getFirst()).isSameAs(raw.reply);
        exporter.reset();

        raw.reply.complete("ok");

        var send = single();
        assertThat(send.getName()).isEqualTo("send PlaceOrder");
        assertThat(send.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(send.getParentSpanId()).isEqualTo(handler.getSpanId());
        assertThat(send.getTraceId()).isEqualTo(handler.getTraceId());
        assertThat(send.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        // The receiver's parent is the send span, not the handler's.
        assertThat(raw.sent.getFirst().get("traceparent")).contains(send.getSpanId());
    }

    @Test
    void aFailedReplyMarksTheSendSpan() {
        var raw = new RecordingGateway();
        var gateway = CorrelatingGateways.commands(raw, agent);
        gateway.send(new PlaceOrder());
        raw.reply.completeExceptionally(new IllegalStateException("aggregate o1 is closed"));

        var send = single();
        assertThat(send.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(send.getEvents()).anyMatch(e -> e.getName().equals("exception"));
    }

    @Test
    void aQueryIsAQuerySpan() {
        var raw = new RecordingGateway();
        CorrelatingGateways.queries(raw, agent).query(new FindOrder());
        raw.reply.complete(null);

        assertThat(single().getName()).isEqualTo("query FindOrder");
    }

    @Test
    void withoutACurrentSpanTheSendJoinsTheTraceOfTheHandledMessage() {
        var handled = new DomainEventMessage(new OrderPlaced());
        var meta = new Metadata();
        meta.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        handled.setMetadata(meta);

        agent.traceSend(new PlaceOrder(), null, handled, m -> CompletableFuture.completedFuture(null));

        var send = single();
        assertThat(send.getTraceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(send.getParentSpanId()).isEqualTo("b7ad6b7169203331");
    }

    @Test
    void aSendThatThrowsEndsItsSpan() {
        assertThatThrownBy(() -> agent.traceSend(new PlaceOrder(), null, null, m -> {
            throw new IllegalArgumentException("bad key");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(single().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void theNoOpAgentSendsTheMetadataAsItIs() {
        var meta = new Metadata();
        meta.put("tenant", "acme");
        var seen = new ArrayList<Metadata>();
        new TracingAgent("orders", 1).traceSend(new PlaceOrder(), meta, null, m -> {
            seen.add(m);
            return CompletableFuture.completedFuture(null);
        });
        assertThat(seen).singleElement().isSameAs(meta);
    }

    private SpanData single() {
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        return spans.getFirst();
    }
}
