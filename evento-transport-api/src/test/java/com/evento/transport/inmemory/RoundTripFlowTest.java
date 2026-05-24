package com.evento.transport.inmemory;

import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end flow test using an in-memory transport pair to simulate an
 * Evento-style RPC round-trip:
 *
 * <pre>
 *   client                               server
 *     │                                    │
 *     │ Request(CreateDemoCommand) ──────► │ decode → handle
 *     │                                    │ produce DemoCreatedEvent
 *     │ ◄────────── Response(event)        │
 *     │ decode + verify                    │
 * </pre>
 *
 * <p>The payload classes ({@link CreateDemoCommand}, {@link DemoCreatedEvent})
 * mirror the demo bundle in {@code evento-demo-api}. They are intentionally
 * defined locally so the test does not depend on the demo module.
 */
class RoundTripFlowTest {

    sealed interface BusinessPayload permits CreateDemoCommand, DemoCreatedEvent {}

    record CreateDemoCommand(String demoId, String name, long value) implements BusinessPayload {
        @JsonCreator
        CreateDemoCommand(
                @JsonProperty("demoId") String demoId,
                @JsonProperty("name") String name,
                @JsonProperty("value") long value
        ) {
            this.demoId = demoId;
            this.name = name;
            this.value = value;
        }
    }

    record DemoCreatedEvent(String demoId, String name, long value, long createdAt) implements BusinessPayload {
        @JsonCreator
        DemoCreatedEvent(
                @JsonProperty("demoId") String demoId,
                @JsonProperty("name") String name,
                @JsonProperty("value") long value,
                @JsonProperty("createdAt") long createdAt
        ) {
            this.demoId = demoId;
            this.name = name;
            this.value = value;
            this.createdAt = createdAt;
        }
    }

    private InMemoryTransport.Pair pair;
    private PayloadCodec payloadCodec;
    private ConcurrentHashMap<UUID, CompletableFuture<Response>> pending;

    @BeforeEach
    void setUp() {
        pair = InMemoryTransport.pair("bundle-test", "server-test");
        payloadCodec = new JacksonCborPayloadCodec();
        pending = new ConcurrentHashMap<>();

        // Client-side: route inbound Response back to the awaiting CompletableFuture.
        pair.client().onMessage(msg -> {
            if (msg instanceof Response r) {
                var future = pending.remove(r.correlationId());
                if (future != null) future.complete(r);
            }
        });

        pair.client().connect().join();
        pair.server().connect().join();
    }

    @AfterEach
    void tearDown() {
        pair.client().close();
        pair.server().close();
    }

    @Test
    void roundTripCommandThenEventResponse() throws Exception {
        // Server: handle CreateDemoCommand → reply with DemoCreatedEvent.
        pair.server().onMessage(msg -> {
            if (!(msg instanceof Request req)) return;
            CreateDemoCommand cmd = payloadCodec.decode(req.payload(), CreateDemoCommand.class);
            var event = new DemoCreatedEvent(cmd.demoId(), cmd.name(), cmd.value(), 1_700_000_000_000L);
            byte[] body = payloadCodec.encode(event);
            var response = Response.success(req.correlationId(), DemoCreatedEvent.class.getName(), body);
            pair.server().send(response).join();
        });

        // Client: encode + send + await response.
        var cmd = new CreateDemoCommand("demo-42", "Hello", 99L);
        var correlationId = UUID.randomUUID();
        var future = new CompletableFuture<Response>();
        pending.put(correlationId, future);

        var request = new Request(
                correlationId,
                "bundle-test", "instance-1", "1.0.0",
                CreateDemoCommand.class.getName(),
                payloadCodec.encode(cmd),
                /* timeoutMs */ 5_000L,
                System.currentTimeMillis()
        );
        pair.client().send(request).join();

        Response response = future.get(2, TimeUnit.SECONDS);

        assertThat(response.isError()).isFalse();
        assertThat(response.correlationId()).isEqualTo(correlationId);
        assertThat(response.payloadType()).isEqualTo(DemoCreatedEvent.class.getName());

        DemoCreatedEvent decoded = payloadCodec.decode(response.payload(), DemoCreatedEvent.class);
        assertThat(decoded.demoId()).isEqualTo("demo-42");
        assertThat(decoded.name()).isEqualTo("Hello");
        assertThat(decoded.value()).isEqualTo(99L);
        assertThat(decoded.createdAt()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void handlerExceptionPropagatesAsErrorResponse() throws Exception {
        // Server: any request → error response.
        pair.server().onMessage(msg -> {
            if (!(msg instanceof Request req)) return;
            try {
                throw new IllegalArgumentException("invalid demo id");
            } catch (Throwable t) {
                var err = Response.failure(req.correlationId(), ResponseError.of(t));
                pair.server().send(err).join();
            }
        });

        var correlationId = UUID.randomUUID();
        var future = new CompletableFuture<Response>();
        pending.put(correlationId, future);

        pair.client().send(new Request(
                correlationId, "bundle-test", "instance-1", "1.0.0",
                CreateDemoCommand.class.getName(),
                payloadCodec.encode(new CreateDemoCommand("bad", "x", 0L)),
                5_000L, System.currentTimeMillis()
        )).join();

        Response response = future.get(2, TimeUnit.SECONDS);

        assertThat(response.isError()).isTrue();
        assertThat(response.error().exceptionClassName()).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(response.error().message()).isEqualTo("invalid demo id");
        assertThat(response.error().stackTrace()).contains("RoundTripFlowTest");
    }

    @Test
    void parallelRequestsCorrelateIndependently() throws Exception {
        pair.server().onMessage(msg -> {
            if (!(msg instanceof Request req)) return;
            CreateDemoCommand cmd = payloadCodec.decode(req.payload(), CreateDemoCommand.class);
            // Echo: encode value*2 into a DemoCreatedEvent so we can verify per-request mapping.
            var event = new DemoCreatedEvent(cmd.demoId(), cmd.name(), cmd.value() * 2, 0L);
            pair.server().send(Response.success(req.correlationId(), DemoCreatedEvent.class.getName(),
                    payloadCodec.encode(event))).join();
        });

        int n = 64;
        var futures = new CompletableFuture[n];
        var ids = new UUID[n];
        for (int i = 0; i < n; i++) {
            ids[i] = UUID.randomUUID();
            futures[i] = new CompletableFuture<Response>();
            pending.put(ids[i], futures[i]);
            pair.client().send(new Request(
                    ids[i], "bundle-test", "instance-1", "1.0.0",
                    CreateDemoCommand.class.getName(),
                    payloadCodec.encode(new CreateDemoCommand("d-" + i, "n", i)),
                    5_000L, System.currentTimeMillis()
            ));
        }
        CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);

        for (int i = 0; i < n; i++) {
            Response r = (Response) futures[i].get();
            assertThat(r.correlationId()).isEqualTo(ids[i]);
            DemoCreatedEvent e = payloadCodec.decode(r.payload(), DemoCreatedEvent.class);
            assertThat(e.value()).isEqualTo(i * 2L);
            assertThat(e.demoId()).isEqualTo("d-" + i);
        }
    }

    @Test
    void sendWhileDisconnectedFails() {
        pair.client().simulateDisconnect();
        var req = new Request(UUID.randomUUID(), "bundle-test", "instance-1", "1.0.0",
                CreateDemoCommand.class.getName(),
                payloadCodec.encode(new CreateDemoCommand("x", "y", 0L)),
                1_000L, System.currentTimeMillis());

        assertThatThrownBy(() -> pair.client().send(req))
                .isInstanceOf(com.evento.transport.SendFailedException.class)
                .hasMessageContaining("DISCONNECTED");
    }

    @Test
    void injectedSendFailureBubblesUpThroughFuture() {
        pair.server().onMessage(msg -> {});  // server inert
        pair.client().failNextSends(1);
        var future = pair.client().send(new Request(
                UUID.randomUUID(), "bundle-test", "instance-1", "1.0.0",
                "noop", new byte[0], 1_000L, System.currentTimeMillis()));

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(com.evento.transport.SendFailedException.class);
    }
}
