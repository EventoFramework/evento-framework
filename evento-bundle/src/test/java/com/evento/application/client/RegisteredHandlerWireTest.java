package com.evento.application.client;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The produced events of a handler on the wire (3.0.1): they reach a broker that reads them, a
 * handler without any adds nothing to the discovery, and a broker that predates the field (its
 * RegisteredHandler has none) still decodes the rest.
 */
class RegisteredHandlerWireTest {

    private final JacksonCborPayloadCodec codec = new JacksonCborPayloadCodec();

    @Test
    void theProducedEventsTravelWithTheHandler() throws Exception {
        var decoded = codec.decode(codec.encode(checkOut(List.of("ItemReserved", "CouponUsed"))), RegisteredHandler.class);

        assertThat(decoded.getProducedEvents()).containsExactly("ItemReserved", "CouponUsed");
        assertThat(decoded.getReturnType()).isEqualTo("ItemReserved");
    }

    @Test
    void aHandlerWithoutProducedEventsPutsNothingOnTheWire() throws Exception {
        var tree = JacksonCborPayloadCodec.defaultMapper().readTree(codec.encode(checkOut(List.of())));

        assertThat(tree.has("producedEvents")).isFalse();
    }

    @Test
    void aBrokerThatPredatesTheFieldDecodesTheRest() throws Exception {
        var older = codec.decode(codec.encode(checkOut(List.of("ItemReserved", "CouponUsed"))), Before301.class);

        assertThat(older.getHandledPayload()).isEqualTo("CheckOut");
        assertThat(older.getReturnType()).isEqualTo("ItemReserved");
    }

    private static RegisteredHandler checkOut(List<String> produced) {
        var h = new RegisteredHandler(ComponentType.Aggregate, "Basket", HandlerType.AggregateCommandHandler,
                PayloadType.DomainCommand, "CheckOut", "ItemReserved", false, null);
        h.setProducedEvents(produced);
        return h;
    }

    /** RegisteredHandler as a 3.0.0 broker knows it: no producedEvents. */
    public static class Before301 {
        private String handledPayload;
        private String returnType;

        public String getHandledPayload() { return handledPayload; }
        public void setHandledPayload(String handledPayload) { this.handledPayload = handledPayload; }
        public String getReturnType() { return returnType; }
        public void setReturnType(String returnType) { this.returnType = returnType; }
    }
}
