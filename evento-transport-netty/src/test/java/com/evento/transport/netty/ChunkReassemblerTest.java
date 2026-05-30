package com.evento.transport.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChunkReassembler}'s resource-safety bounds: a misbehaving peer must not be
 * able to exhaust heap by streaming oversized or never-completed chunked messages.
 */
class ChunkReassemblerTest {

    /** Builds a single CHUNK frame: [0x01][msb][lsb][isLast][data]. */
    private static ByteBuf chunk(UUID stream, boolean last, byte[] data) {
        ByteBuf b = Unpooled.buffer();
        b.writeByte(0x01);
        b.writeLong(stream.getMostSignificantBits());
        b.writeLong(stream.getLeastSignificantBits());
        b.writeByte(last ? 0x01 : 0x00);
        b.writeBytes(data);
        return b;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void reassemblesMultipleChunksInOrder() {
        var ch = new EmbeddedChannel(new ChunkReassembler());
        UUID s = UUID.randomUUID();

        assertThat(ch.writeInbound(chunk(s, false, bytes("hello ")))).isFalse(); // not last → no output yet
        assertThat(ch.writeInbound(chunk(s, true, bytes("world")))).isTrue();

        ByteBuf out = ch.readInbound();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("hello world");
        out.release();
        assertThat(ch.finishAndReleaseAll()).isFalse();
    }

    @Test
    void closesChannelWhenSingleMessageExceedsMaxBytes() {
        // 8-byte cap; first 5-byte chunk is fine, second pushes past the cap.
        var ch = new EmbeddedChannel(new ChunkReassembler(8, 16, Long.MAX_VALUE));
        UUID s = UUID.randomUUID();

        ch.writeInbound(chunk(s, false, bytes("12345")));
        assertThat(ch.isOpen()).isTrue();

        ch.writeInbound(chunk(s, false, bytes("6789")));   // 5 + 4 = 9 > 8
        assertThat(ch.isOpen()).as("over-sized reassembly must close the channel").isFalse();
        assertThat((Object) ch.readInbound()).as("no partial message is emitted").isNull();

        ch.finishAndReleaseAll();
    }

    @Test
    void closesChannelWhenTooManyConcurrentStreams() {
        // Cap of 2 concurrent incomplete streams.
        var ch = new EmbeddedChannel(new ChunkReassembler(1024, 2, Long.MAX_VALUE));

        ch.writeInbound(chunk(UUID.randomUUID(), false, bytes("a")));
        ch.writeInbound(chunk(UUID.randomUUID(), false, bytes("b")));
        assertThat(ch.isOpen()).isTrue();

        ch.writeInbound(chunk(UUID.randomUUID(), false, bytes("c"))); // 3rd stream → over cap
        assertThat(ch.isOpen()).as("exceeding concurrent-stream cap must close the channel").isFalse();

        ch.finishAndReleaseAll();
    }

    @Test
    void evictsStalePartialStreams() {
        // Zero TTL: any pre-existing partial is stale the moment a new stream is opened.
        var ch = new EmbeddedChannel(new ChunkReassembler(1024, 16, 0L));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        ch.writeInbound(chunk(a, false, bytes("X")));   // partial A = "X"
        ch.writeInbound(chunk(b, false, bytes("ignore"))); // opening B evicts the stale A
        // A now re-enters as a fresh stream; its final chunk carries only "Y".
        assertThat(ch.writeInbound(chunk(a, true, bytes("Y")))).isTrue();

        ByteBuf out = ch.readInbound();
        assertThat(out.toString(StandardCharsets.UTF_8))
                .as("the evicted 'X' must not be prepended")
                .isEqualTo("Y");
        out.release();

        ch.finishAndReleaseAll();
    }
}
