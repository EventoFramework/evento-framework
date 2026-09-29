package com.evento.common.admin;

import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The idempotency key (G5) is a field added to {@code CommandMessage} in a rolling-upgrade world:
 * bundles and servers of both versions exchange command envelopes through {@link AdminPayloadCodec}.
 * Both skews must degrade to "no key", never to a rejected command.
 */
class IdempotencyKeyCodecToleranceTest {

    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final ObjectMapper cbor = new ObjectMapper(new CBORFactory());

    public static class PayCmd extends ServiceCommand {
        public String orderId;

        public PayCmd() {
        }

        PayCmd(String orderId) {
            this.orderId = orderId;
        }
    }

    public static class CreateCmd extends DomainCommand {
        public String orderId;

        public CreateCmd() {
        }

        CreateCmd(String orderId) {
            this.orderId = orderId;
        }

        @Override
        public String getAggregateId() {
            return orderId;
        }
    }

    private static EventoRequest envelope(CommandMessage<?> body) {
        var request = new EventoRequest();
        request.setCorrelationId("c-1");
        request.setSourceBundleId("b");
        request.setSourceInstanceId("i");
        request.setBody(body);
        request.setTimeout(5);
        request.setUnit(TimeUnit.SECONDS);
        return request;
    }

    @Test
    void theKeyTravelsWithTheCommand() {
        var msg = new ServiceCommandMessage(new PayCmd("o-1"));
        msg.setIdempotencyKey("pay-o-1");
        var decoded = (ServiceCommandMessage) codec.decodeRequest(codec.encodeRequest(envelope(msg))).getBody();
        assertThat(decoded.getIdempotencyKey()).isEqualTo("pay-o-1");
        assertThat(((PayCmd) decoded.getPayload()).orderId).isEqualTo("o-1");
    }

    @Test
    void aCommandFromAnOlderPeerHasNoKey() throws Exception {
        // An older peer's envelope: the same bytes without the field at all.
        var msg = new DomainCommandMessage(new CreateCmd("o-2"));
        msg.setIdempotencyKey("create-o-2");
        var tree = cbor.readTree(codec.encodeRequest(envelope(msg)));
        assertThat(removeField(tree, "idempotencyKey")).as("the field was on the wire").isPositive();

        var decoded = (DomainCommandMessage) codec.decodeRequest(cbor.writeValueAsBytes(tree)).getBody();
        assertThat(decoded.getIdempotencyKey()).isNull();
        assertThat(decoded.getAggregateId()).isEqualTo("o-2");
    }

    @Test
    void aCommandFromANewerPeerWithFieldsWeDoNotKnowIsStillDecoded() throws Exception {
        // How an older peer sees our key: an unknown property on a known type, skipped. Stand-in
        // here: a property this version has never heard of, next to the key.
        var msg = new ServiceCommandMessage(new PayCmd("o-3"));
        msg.setIdempotencyKey("pay-o-3");
        var tree = cbor.readTree(codec.encodeRequest(envelope(msg)));
        assertThat(addFieldNextTo(tree, "idempotencyKey", "idempotencyScopeFromTheFuture")).isPositive();

        var decoded = (ServiceCommandMessage) codec.decodeRequest(cbor.writeValueAsBytes(tree)).getBody();
        assertThat(decoded.getIdempotencyKey()).isEqualTo("pay-o-3");
    }

    @Test
    void blankMeansNoKeyAndOverlongKeysAreRefused() throws Exception {
        var msg = new ServiceCommandMessage(new PayCmd("o-4"));
        msg.setIdempotencyKey("   ");
        assertThat(msg.getIdempotencyKey()).isNull();

        // The codec sets fields directly, bypassing the setter: a blank key off the wire is no key too.
        msg.setIdempotencyKey("k");
        var tree = cbor.readTree(codec.encodeRequest(envelope(msg)));
        replaceText(tree, "idempotencyKey", " ");
        var decoded = (ServiceCommandMessage) codec.decodeRequest(cbor.writeValueAsBytes(tree)).getBody();
        assertThat(decoded.getIdempotencyKey()).isNull();

        assertThatThrownBy(() -> msg.setIdempotencyKey("k".repeat(CommandMessage.MAX_IDEMPOTENCY_KEY_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int removeField(JsonNode node, String field) {
        int n = 0;
        if (node instanceof ObjectNode obj && obj.has(field)) {
            obj.remove(field);
            n++;
        }
        for (var child : node) n += removeField(child, field);
        return n;
    }

    private static int addFieldNextTo(JsonNode node, String existing, String added) {
        int n = 0;
        if (node instanceof ObjectNode obj && obj.has(existing)) {
            obj.put(added, "x");
            n++;
        }
        for (var child : node) n += addFieldNextTo(child, existing, added);
        return n;
    }

    private static void replaceText(JsonNode node, String field, String value) {
        if (node instanceof ObjectNode obj && obj.has(field)) obj.put(field, value);
        for (var child : node) replaceText(child, field, value);
    }
}
