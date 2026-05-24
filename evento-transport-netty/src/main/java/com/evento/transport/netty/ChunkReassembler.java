package com.evento.transport.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound handler that strips the 1-byte frame-type discriminator added by {@link ChunkingEncoder}
 * and reassembles multi-chunk streams before passing them downstream to {@link CborMessageDecoder}.
 *
 * <p>Frame types:
 * <ul>
 *   <li>{@code 0x00} FULL — passes the remaining bytes directly to {@code CborMessageDecoder}</li>
 *   <li>{@code 0x01} CHUNK — accumulates chunks by stream UUID; emits the reassembled buffer
 *       when the last chunk arrives</li>
 * </ul>
 *
 * <p>State is per-channel (one instance per connection). No thread-safety is needed because
 * Netty guarantees that a channel's pipeline handlers are invoked on the same event-loop thread.
 */
final class ChunkReassembler extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ChunkReassembler.class);

    private final Map<UUID, ByteArrayOutputStream> pending = new HashMap<>();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable()) return;
        byte type = in.readByte();
        switch (type) {
            case 0x00 -> out.add(in.retain()); // FULL: pass CBOR bytes to CborMessageDecoder
            case 0x01 -> {
                long msb = in.readLong();
                long lsb = in.readLong();
                UUID streamId = new UUID(msb, lsb);
                boolean isLast = in.readByte() == 0x01;

                ByteArrayOutputStream acc = pending.computeIfAbsent(streamId, k -> new ByteArrayOutputStream());
                int dataLen = in.readableBytes();
                byte[] data = new byte[dataLen];
                in.readBytes(data);
                acc.write(data, 0, dataLen);

                if (isLast) {
                    pending.remove(streamId);
                    out.add(Unpooled.wrappedBuffer(acc.toByteArray()));
                }
            }
            default -> {
                log.error("event=unknown_frame_type type=0x{} channel={}", String.format("%02X", type), ctx.channel().id());
                ctx.close();
            }
        }
    }
}
