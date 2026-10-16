package com.evento.transport.netty;

import com.evento.transport.codec.Codec;
import com.evento.transport.message.Message;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * Serializes an outbound {@link Message} into raw bytes via the {@link Codec}.
 * A downstream {@code LengthFieldPrepender} will add the 4-byte length prefix
 * before the bytes hit the socket.
 *
 * <p>The codec's array is wrapped, not copied into a pooled buffer: for a large
 * reply that copy was a second full-size allocation per send, for nothing.
 */
final class CborMessageEncoder extends MessageToMessageEncoder<Message> {

    private final Codec codec;

    CborMessageEncoder(Codec codec) {
        this.codec = codec;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) {
        out.add(Unpooled.wrappedBuffer(codec.encode(msg)));
    }
}
