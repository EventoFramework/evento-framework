package com.evento.transport.codec;

import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Mixed-version wire tolerance.
 *
 * <p>A rolling upgrade always runs some bundles ahead of the broker (or behind it). Both
 * skews must degrade to "the peer ignores what it does not understand", never to a rejected
 * payload — the original failure mode was silent: the bundle registered, enabled and
 * consumed normally while its entire handler metadata vanished from the dashboard.
 */
class CodecVersionToleranceTest {

    private final JacksonCborPayloadCodec codec = new JacksonCborPayloadCodec();

    /** Encodes arbitrary maps, standing in for a peer that knows fields we do not. */
    private static byte[] cbor(Object value) throws Exception {
        return new ObjectMapper(new CBORFactory()).writeValueAsBytes(value);
    }

    @Test
    void decodesAPayloadFromANewerPeerCarryingUnknownFields() throws Exception {
        var handler = new LinkedHashMap<String, Object>();
        handler.put("componentName", "OrderProjector");
        handler.put("handledPayload", "OrderCreatedEvent");
        handler.put("executor", "read-model");
        // Fields this version has never heard of — e.g. a derived getter or a genuinely
        // new protocol field added by a newer bundle.
        handler.put("async", true);
        handler.put("somethingFromTheFuture", Map.of("nested", List.of(1, 2, 3)));

        var discovery = new LinkedHashMap<String, Object>();
        discovery.put("bundleVersion", 7L);
        discovery.put("handlers", List.of(handler));
        discovery.put("unknownTopLevelField", "ignored");

        var decoded = codec.decode(cbor(discovery), BundleDiscoveryInfo.class);

        assertThat(decoded.bundleVersion()).isEqualTo(7L);
        assertThat(decoded.handlers()).hasSize(1);
        // The known fields still arrive — the unknown ones are dropped, not fatal.
        assertThat(decoded.handlers().getFirst().getExecutor()).isEqualTo("read-model");
        assertThat(decoded.handlers().getFirst().getComponentName()).isEqualTo("OrderProjector");
    }

    @Test
    void decodesAPayloadFromAnOlderPeerMissingFieldsEntirely() throws Exception {
        // An older bundle that predates executor/repositoryUrl/linePrefix.
        var handler = new LinkedHashMap<String, Object>();
        handler.put("componentName", "LegacyProjector");
        handler.put("handledPayload", "OrderCreatedEvent");

        var discovery = new LinkedHashMap<String, Object>();
        discovery.put("bundleVersion", 1L);
        discovery.put("handlers", List.of(handler));

        var decoded = codec.decode(cbor(discovery), BundleDiscoveryInfo.class);

        // Records normalise null to a default in their @JsonCreator; the bean field keeps
        // its initialiser. Neither should surface as null to callers.
        assertThat(decoded.linePrefix()).isEqualTo("L");
        assertThat(decoded.description()).isEmpty();
        assertThat(decoded.payloadInfo()).isEmpty();
        assertThat(decoded.handlers().getFirst().getExecutor()).isEmpty();
        assertThat(decoded.handlers().getFirst().getInvokedCommands()).isEmpty();
    }

    @Test
    void roundTripsTheCurrentShapeUnchanged() {
        var handler = new RegisteredHandler();
        handler.setComponentName("OrderProjector");
        handler.setHandledPayload("OrderCreatedEvent");
        handler.setExecutor("read-model");

        var original = new BundleDiscoveryInfo(3L, "desc", "detail", "https://example/repo",
                "L", List.of(handler), Map.of());

        var decoded = codec.decode(codec.encode(original), BundleDiscoveryInfo.class);

        assertThat(decoded.handlers().getFirst().getExecutor()).isEqualTo("read-model");
        assertThat(decoded.repositoryUrl()).isEqualTo("https://example/repo");
    }

    @Test
    void messageCodecAlsoToleratesUnknownFieldsSoTheHandshakeSurvivesAVersionSkew() throws Exception {
        // Derive the wire shape from a real Hello rather than hand-writing it, then splice
        // in a field as a newer peer would. Hand-built maps drift from the record.
        var hello = new com.evento.transport.message.Hello(
                java.util.UUID.randomUUID(), (byte) 1, "orders", "orders-1", "1",
                java.util.Set.of("ping-pong"), "", 0L);

        var mapper = JacksonCborCodec.defaultMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap = mapper.readValue(
                mapper.writeValueAsBytes((com.evento.transport.message.Message) hello), Map.class);

        var fromTheFuture = new LinkedHashMap<>(asMap);
        fromTheFuture.put("fieldFromTheFuture", "ignored");
        var bytes = mapper.writeValueAsBytes(fromTheFuture);

        var codec = new JacksonCborCodec();
        assertThatCode(() -> codec.decode(bytes, 0, bytes.length)).doesNotThrowAnyException();

        var decoded = (com.evento.transport.message.Hello) codec.decode(bytes, 0, bytes.length);
        assertThat(decoded.bundleId()).isEqualTo("orders");
        assertThat(decoded.capabilities()).containsExactly("ping-pong");
    }
}
