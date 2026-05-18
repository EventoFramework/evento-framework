package com.evento.transport.codec;

import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Ping;
import com.evento.transport.message.Pong;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import com.evento.transport.message.Welcome;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonCborCodecTest {

    private final JacksonCborCodec codec = new JacksonCborCodec();

    @Test
    void roundTripHello() {
        var hello = new Hello(UUID.randomUUID(), (byte) 2, "bundle-x", "inst-1",
                "1.0.0", Set.of("lz4", "ping-pong"), 1700000000000L);
        var out = codec.decode(codec.encode(hello));
        assertThat(out).isInstanceOf(Hello.class).isEqualTo(hello);
    }

    @Test
    void roundTripWelcome() {
        var welcome = new Welcome(UUID.randomUUID(), (byte) 2, "server-x", Set.of("lz4"), 1L);
        assertThat(codec.decode(codec.encode(welcome))).isEqualTo(welcome);
    }

    @Test
    void roundTripReject() {
        var reject = new Reject(UUID.randomUUID(), Reject.CODE_PROTOCOL_VERSION, "v3 not supported", 1L);
        assertThat(codec.decode(codec.encode(reject))).isEqualTo(reject);
    }

    @Test
    void roundTripPing() {
        var ping = new Ping(UUID.randomUUID(), 7L, 1234L);
        assertThat(codec.decode(codec.encode(ping))).isEqualTo(ping);
    }

    @Test
    void roundTripPong() {
        var pong = new Pong(UUID.randomUUID(), 7L, 1235L, 1234L);
        assertThat(codec.decode(codec.encode(pong))).isEqualTo(pong);
    }

    @Test
    void roundTripRequestWithPayload() {
        byte[] payload = "hello payload".getBytes();
        var req = new Request(UUID.randomUUID(), "bundle", "inst", "v",
                "com.demo.Cmd", payload, 5000L, 1L);
        var decoded = (Request) codec.decode(codec.encode(req));
        assertThat(decoded.correlationId()).isEqualTo(req.correlationId());
        assertThat(decoded.payloadType()).isEqualTo("com.demo.Cmd");
        assertThat(decoded.payload()).isEqualTo(payload);
        assertThat(decoded.timeoutMillis()).isEqualTo(5000L);
    }

    @Test
    void roundTripResponseSuccess() {
        var resp = Response.ok(UUID.randomUUID(), "com.demo.Event", new byte[]{1, 2, 3});
        var decoded = (Response) codec.decode(codec.encode(resp));
        assertThat(decoded.isError()).isFalse();
        assertThat(decoded.payload()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void roundTripResponseError() {
        var err = ResponseError.of(new IllegalStateException("bad"));
        var resp = Response.error(UUID.randomUUID(), err);
        var decoded = (Response) codec.decode(codec.encode(resp));
        assertThat(decoded.isError()).isTrue();
        assertThat(decoded.error().exceptionClassName()).isEqualTo(IllegalStateException.class.getName());
        assertThat(decoded.error().message()).isEqualTo("bad");
    }

    @Test
    void roundTripNotification() {
        var n = new Notification(UUID.randomUUID(), "com.demo.Kill", new byte[]{0xA, 0xB}, 1L);
        var decoded = (Notification) codec.decode(codec.encode(n));
        assertThat(decoded.payloadType()).isEqualTo("com.demo.Kill");
        assertThat(decoded.payload()).isEqualTo(new byte[]{0xA, 0xB});
    }

    @Test
    void typeDiscriminatorIsPresentOnWire() {
        var ping = new Ping(UUID.randomUUID(), 1L, 1L);
        byte[] bytes = codec.encode(ping);
        // CBOR maps include keys; the @t discriminator must appear somewhere in the byte stream.
        String asString = new String(bytes);
        assertThat(asString).contains("@t");
    }

    @Test
    void emptyFrameRejected() {
        assertThatThrownBy(() -> codec.decode(new byte[0]))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void garbageFrameRejected() {
        assertThatThrownBy(() -> codec.decode(new byte[]{0x00, 0x01, 0x02, 0x03}))
                .isInstanceOf(CodecException.class);
    }

    @Test
    void encodeNullRejected() {
        assertThatThrownBy(() -> codec.encode((Message) null))
                .isInstanceOf(CodecException.class);
    }

    @Test
    void offsetBoundedDecodeWorks() {
        var ping = new Ping(UUID.randomUUID(), 99L, 1L);
        byte[] body = codec.encode(ping);
        byte[] padded = new byte[body.length + 8];
        System.arraycopy(body, 0, padded, 4, body.length);
        var decoded = (Ping) codec.decode(padded, 4, body.length);
        assertThat(decoded.sequence()).isEqualTo(99L);
    }
}
