package com.evento.transport.netty;

import com.evento.transport.Frame;
import com.evento.transport.message.Ping;
import com.evento.transport.message.Pong;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Symmetric heartbeat: on writer-idle, send a {@link Ping}; on reader-idle, close
 * the channel (peer presumed dead). Inbound {@link Ping}s are auto-replied with a
 * {@link Pong} at this handler so the application layer never sees them.
 *
 * <p>Inbound {@link Pong}s are passed through unchanged — the application may use
 * them for RTT metrics. The reader-idle clock is reset on any inbound byte by
 * Netty's {@code IdleStateHandler}, so we don't need explicit Pong handling here.
 */
final class HeartbeatHandler extends ChannelDuplexHandler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatHandler.class);

    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idle) {
            if (idle.state() == IdleState.WRITER_IDLE) {
                long seq = sequence.incrementAndGet();
                var ping = new Ping(UUID.randomUUID(), seq, System.currentTimeMillis());
                ctx.writeAndFlush(ping);
                log.trace("event=ping_sent channel={} seq={}", ctx.channel().id(), seq);
                return;
            }
            if (idle.state() == IdleState.READER_IDLE) {
                log.warn("event=reader_idle_close channel={} reason=heartbeat_timeout",
                        ctx.channel().id());
                ctx.close();
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Frame frame && frame.message() instanceof Ping incoming) {
            var pong = new Pong(incoming.correlationId(), incoming.sequence(),
                    System.currentTimeMillis(), incoming.timestampMs());
            ctx.writeAndFlush(pong);
            log.trace("event=pong_replied channel={} seq={}", ctx.channel().id(), incoming.sequence());
            return;  // swallow Ping; do not propagate to application handler
        }
        super.channelRead(ctx, msg);
    }
}
