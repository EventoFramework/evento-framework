package com.evento.transport.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
 *
 * <p><b>Resource safety.</b> Chunking deliberately imposes no hard message-size limit, which means
 * naive reassembly is an unbounded-memory (OOM/DoS) vector: a peer can stream non-final chunks
 * across many stream UUIDs and never finish them. This handler bounds that exposure three ways:
 * <ul>
 *   <li>{@code maxMessageBytes} — a single reassembled message may not exceed this size;</li>
 *   <li>{@code maxConcurrentStreams} — the number of simultaneously in-flight (incomplete)
 *       streams on one channel is capped;</li>
 *   <li>{@code partialStreamTtlNanos} — partial streams that go stale (sender stops mid-message)
 *       are evicted, so memory is reclaimed even on an otherwise-active channel.</li>
 * </ul>
 * Any breach of a hard cap closes the channel — a peer that violates the framing contract is not
 * trusted to behave. Buffers are also released when the channel goes inactive.
 */
final class ChunkReassembler extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ChunkReassembler.class);

    /** Default cap on a single reassembled message (256 MiB). Above the framework's large-payload tests. */
    static final int DEFAULT_MAX_MESSAGE_BYTES = 256 * 1024 * 1024;
    /** Default cap on concurrent incomplete streams per channel. Normal traffic uses 1; this is generous. */
    static final int DEFAULT_MAX_CONCURRENT_STREAMS = 1024;
    /** Default time-to-live for a partial (incomplete) stream before it is evicted. */
    static final long DEFAULT_PARTIAL_STREAM_TTL_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final int maxMessageBytes;
    private final int maxConcurrentStreams;
    private final long partialStreamTtlNanos;

    private final Map<UUID, Partial> pending = new HashMap<>();

    ChunkReassembler() {
        this(DEFAULT_MAX_MESSAGE_BYTES, DEFAULT_MAX_CONCURRENT_STREAMS, DEFAULT_PARTIAL_STREAM_TTL_NANOS);
    }

    ChunkReassembler(int maxMessageBytes, int maxConcurrentStreams, long partialStreamTtlNanos) {
        this.maxMessageBytes = maxMessageBytes;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.partialStreamTtlNanos = partialStreamTtlNanos;
    }

    /** A partial stream: the accumulated bytes plus the time the first chunk arrived (for TTL eviction). */
    private static final class Partial {
        final ByteArrayOutputStream acc = new ByteArrayOutputStream();
        final long startedNanos;

        Partial(long startedNanos) {
            this.startedNanos = startedNanos;
        }
    }

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
                int dataLen = in.readableBytes();

                Partial acc = pending.get(streamId);
                if (acc == null) {
                    long now = System.nanoTime();
                    evictExpired(now);
                    if (pending.size() >= maxConcurrentStreams) {
                        log.error("event=chunk_stream_limit_exceeded limit={} channel={}",
                                maxConcurrentStreams, ctx.channel().id());
                        ctx.close();
                        return;
                    }
                    acc = new Partial(now);
                    pending.put(streamId, acc);
                }

                if ((long) acc.acc.size() + dataLen > maxMessageBytes) {
                    log.error("event=chunk_message_too_large limit={} stream={} channel={}",
                            maxMessageBytes, streamId, ctx.channel().id());
                    pending.remove(streamId);
                    ctx.close();
                    return;
                }

                byte[] data = new byte[dataLen];
                in.readBytes(data);
                acc.acc.write(data, 0, dataLen);

                if (isLast) {
                    pending.remove(streamId);
                    out.add(Unpooled.wrappedBuffer(acc.acc.toByteArray()));
                }
            }
            default -> {
                log.error("event=unknown_frame_type type=0x{} channel={}", String.format("%02X", type), ctx.channel().id());
                ctx.close();
            }
        }
    }

    /** Removes partial streams whose first chunk arrived longer ago than the TTL. */
    private void evictExpired(long now) {
        if (pending.isEmpty()) return;
        Iterator<Map.Entry<UUID, Partial>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Partial> e = it.next();
            if (now - e.getValue().startedNanos > partialStreamTtlNanos) {
                log.warn("event=chunk_stream_expired stream={} bytes={}", e.getKey(), e.getValue().acc.size());
                it.remove();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Free any buffers held for incomplete streams when the connection drops.
        pending.clear();
        super.channelInactive(ctx);
    }
}
