package com.evento.transport.netty;

import com.evento.transport.codec.Codec;
import com.evento.transport.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Serializes an outbound {@link Message} into raw bytes via the {@link Codec}.
 * A downstream {@code LengthFieldPrepender} will add the 4-byte length prefix
 * before the bytes hit the socket.
 */
final class CborMessageEncoder extends MessageToByteEncoder<Message> {

    private final Codec codec;

    CborMessageEncoder(Codec codec) {
        this.codec = codec;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) {
        byte[] bytes = codec.encode(msg);
        out.writeBytes(bytes);
    }
}
