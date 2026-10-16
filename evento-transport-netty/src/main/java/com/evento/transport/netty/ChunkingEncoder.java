package com.evento.transport.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;
import java.util.UUID;

/**
 * Outbound handler that transparently splits large messages into fixed-size CHUNK frames
 * and wraps small ones as FULL frames.
 *
 * <p>Wire format (after LengthFieldPrepender adds the 4-byte length prefix):
 * <ul>
 *   <li>FULL frame: {@code [0x00][CBOR bytes...]}</li>
 *   <li>CHUNK frame: {@code [0x01][msb:8][lsb:8][isLast:1][data:N]}</li>
 * </ul>
 *
 * <p>A message is sent as FULL if {@code 1 + cborBytes.length <= maxFrameLength}, otherwise
 * it is split into one or more CHUNK frames, each at most {@code maxFrameLength} bytes
 * (including the 18-byte chunk header).
 */
final class ChunkingEncoder extends MessageToMessageEncoder<ByteBuf> {

    private final int maxFrameLength;
    /**
     * Maximum payload bytes per chunk frame.
     *
     * <p>Netty's {@link io.netty.handler.codec.LengthFieldBasedFrameDecoder} computes the
     * "adjusted frame length" as {@code (value in length prefix) + lengthFieldEndOffset}
     * (= +4 for a 4-byte prefix at offset 0).  The decoder rejects frames where that sum
     * exceeds {@code maxFrameLength}.  So the largest safe payload we can hand to
     * {@link io.netty.handler.codec.LengthFieldPrepender} is {@code maxFrameLength - 4}.
     *
     * <p>A CHUNK frame payload = 18-byte header + chunk data, so:
     * {@code chunkDataCapacity = maxFrameLength - 4 - 18 = maxFrameLength - 22}.
     */
    private final int chunkDataCapacity;

    ChunkingEncoder(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
        this.chunkDataCapacity = maxFrameLength - 22;
    }

    /**
     * Header buffers are allocated, data is never copied: a FULL frame is the 1-byte
     * header composed with the message, a CHUNK frame the 18-byte header composed with a
     * retained slice of it. Each composite is still one buffer, so the downstream
     * {@code LengthFieldPrepender} frames it as one.
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int readable = in.readableBytes();
        if (1 + readable <= maxFrameLength - 4) {
            // fits in one FULL frame
            ByteBuf header = ctx.alloc().buffer(1).writeByte(0x00);
            out.add(ctx.alloc().compositeBuffer(2)
                    .addComponents(true, header, in.retainedSlice()));
            in.skipBytes(readable);
        } else {
            // split into CHUNK frames
            UUID id = UUID.randomUUID();
            long msb = id.getMostSignificantBits();
            long lsb = id.getLeastSignificantBits();
            while (in.isReadable()) {
                int toRead = Math.min(in.readableBytes(), chunkDataCapacity);
                boolean isLast = in.readableBytes() == toRead; // this chunk drains the buffer
                ByteBuf header = ctx.alloc().buffer(18);
                header.writeByte(0x01);
                header.writeLong(msb);
                header.writeLong(lsb);
                header.writeByte(isLast ? 0x01 : 0x00);
                out.add(ctx.alloc().compositeBuffer(2)
                        .addComponents(true, header, in.readRetainedSlice(toRead)));
            }
        }
    }
}
