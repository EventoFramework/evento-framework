package com.evento.transport.netty;

import com.evento.transport.Frame;
import com.evento.transport.codec.Codec;
import com.evento.transport.codec.CodecException;
import com.evento.transport.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Translates a framed {@link ByteBuf} (one CBOR-encoded {@link Message} per
 * invocation, courtesy of the upstream {@code LengthFieldBasedFrameDecoder})
 * into a {@link Frame} — the parsed {@link Message} paired with the raw bytes
 * it was decoded from.
 *
 * <p>Holding the raw bytes enables the zero-copy forwarding path: the broker
 * can route by message metadata and then write the original bytes back out
 * (via {@code Transport.sendRaw}) without re-running the codec. Saves one
 * CBOR encode + one allocation per forwarded message on the broker's hot path.
 *
 * <p>A decode failure is logged and the connection is closed: a malformed frame
 * means we lost stream alignment and continuing to read would produce garbage.
 */
final class CborMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(CborMessageDecoder.class);

    private final Codec codec;

    CborMessageDecoder(Codec codec) {
        this.codec = codec;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int readable = in.readableBytes();
        if (readable == 0) {
            return;
        }
        byte[] bytes = new byte[readable];
        in.readBytes(bytes);
        try {
            Message message = codec.decode(bytes, 0, bytes.length);
            out.add(new Frame(message, bytes));
        } catch (CodecException e) {
            log.error("event=decode_failure channel={} bytes={}", ctx.channel().id(), readable, e);
            ctx.close();
        }
    }
}
