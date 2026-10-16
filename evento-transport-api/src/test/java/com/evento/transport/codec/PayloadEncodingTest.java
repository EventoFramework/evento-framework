package com.evento.transport.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadEncodingTest {

    private static byte[] compressible(int size) {
        var text = "{\"orderId\":\"ORD-000123\",\"status\":\"SHIPPED\",\"lines\":[1,2,3]}"
                .getBytes(StandardCharsets.UTF_8);
        var out = new byte[size];
        for (int i = 0; i < size; i++) out[i] = text[i % text.length];
        return out;
    }

    @Test
    void aLargeCompressiblePayloadRoundTripsSmaller() {
        var original = compressible(1024 * 1024);
        var encoded = PayloadEncoding.deflateIfWorthwhile(original, PayloadEncoding.DEFAULT_THRESHOLD);

        assertThat(PayloadEncoding.isEncoded(encoded)).isTrue();
        assertThat(encoded.length).isLessThan(original.length / 10);
        assertThat(PayloadEncoding.decode(encoded)).isEqualTo(original);
    }

    @Test
    void smallPayloadsAndADisabledThresholdAreLeftAsTheyAre() {
        var small = compressible(1_000);
        assertThat(PayloadEncoding.deflateIfWorthwhile(small, 32 * 1024)).isSameAs(small);
        var large = compressible(100_000);
        assertThat(PayloadEncoding.deflateIfWorthwhile(large, 0)).isSameAs(large);
    }

    @Test
    void anIncompressiblePayloadIsNotEncoded() {
        var noise = new byte[200_000];
        new Random(42).nextBytes(noise);
        assertThat(PayloadEncoding.deflateIfWorthwhile(noise, 1024)).isSameAs(noise);
    }

    @Test
    void aPlainPayloadDecodesToItself() {
        // CBOR never starts with 0xFF; plain bytes are never mistaken for encoded ones.
        var plain = new byte[]{(byte) 0xA2, 1, 2, 3, 4, 5, 6, 7, 8};
        assertThat(PayloadEncoding.isEncoded(plain)).isFalse();
        assertThat(PayloadEncoding.decode(plain)).isSameAs(plain);
    }

    @Test
    void aCorruptPayloadIsRefused() {
        var encoded = PayloadEncoding.deflateIfWorthwhile(compressible(100_000), 1024);
        encoded[encoded.length / 2] ^= 0x5A;
        assertThatThrownBy(() -> PayloadEncoding.decode(encoded)).isInstanceOf(CodecException.class);
    }

    @Test
    void aPayloadDeclaringMoreThanTheBoundIsRefusedBeforeInflating() {
        var encoded = PayloadEncoding.deflateIfWorthwhile(compressible(100_000), 1024);
        assertThatThrownBy(() -> PayloadEncoding.decode(encoded, 50_000))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("bound");
    }
}
