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
 *
 * <p><b>Pre-session gate.</b> On the server side a {@link Ping} that arrives
 * before the peer has sent anything else is a health probe (a bundle checking
 * whether its preferred broker is back, see the bundle's fail-back): no real
 * session pings before its {@code Hello}. Such a Ping is answered only while
 * {@code preSessionReady} says the server is ready to take bundles; otherwise it
 * is dropped and the probe times out. Answering it as soon as the port is bound
 * would call a server "healthy" while its application context — database,
 * event store — is still starting. Once the peer has sent a non-heartbeat frame
 * the gate no longer applies, so a live session's heartbeat never depends on it.
 * Wire compatible: the reply, when sent, is the same Pong as before.
 */
final class HeartbeatHandler extends ChannelDuplexHandler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatHandler.class);

    private final AtomicLong sequence = new AtomicLong(0);
    private final java.util.function.BooleanSupplier preSessionReady;
    /** Whether the peer has sent anything but heartbeats; one handler per channel. */
    private volatile boolean sessionStarted;

    HeartbeatHandler() {
        this(null);
    }

    /** @param preSessionReady gate for Pings that precede any other frame; null answers them all */
    HeartbeatHandler(java.util.function.BooleanSupplier preSessionReady) {
        this.preSessionReady = preSessionReady;
    }

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
            if (!sessionStarted && preSessionReady != null && !isReady()) {
                log.debug("event=probe_ping_unanswered channel={} reason=server_not_ready", ctx.channel().id());
                return;
            }
            var pong = new Pong(incoming.correlationId(), incoming.sequence(),
                    System.currentTimeMillis(), incoming.timestampMs());
            ctx.writeAndFlush(pong);
            log.trace("event=pong_replied channel={} seq={}", ctx.channel().id(), incoming.sequence());
            return;  // swallow Ping; do not propagate to application handler
        }
        if (!sessionStarted && msg instanceof Frame f
                && !(f.message() instanceof com.evento.transport.message.Pong)) {
            sessionStarted = true;
        }
        super.channelRead(ctx, msg);
    }

    private boolean isReady() {
        try {
            return preSessionReady.getAsBoolean();
        } catch (RuntimeException e) {
            log.debug("event=probe_readiness_check_failed cause={}", e.toString());
            return false;
        }
    }
}
