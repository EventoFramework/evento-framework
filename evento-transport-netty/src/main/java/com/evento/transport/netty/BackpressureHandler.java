package com.evento.transport.netty;

import com.evento.transport.state.ConnectionState;
import com.evento.transport.state.ConnectionStateMachine;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reflects Netty's per-channel writability into the {@link ConnectionStateMachine}.
 * When the outbound buffer crosses the high water mark, the channel becomes
 * unwritable and we transition CONNECTED → DEGRADED; callers see the change via
 * {@code Transport.state()} and can drop low-priority traffic.
 *
 * <p>Writability returns to true once the buffer drains below the low water mark.
 * The handler is purely a state mirror; it does not block writes.
 */
final class BackpressureHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(BackpressureHandler.class);

    private final ConnectionStateMachine state;

    BackpressureHandler(ConnectionStateMachine state) {
        this.state = state;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        boolean writable = ctx.channel().isWritable();
        if (writable) {
            state.compareAndTransition(ConnectionState.DEGRADED, ConnectionState.CONNECTED,
                    "channel_writable_recovered");
        } else {
            state.compareAndTransition(ConnectionState.CONNECTED, ConnectionState.DEGRADED,
                    "channel_unwritable_backpressure");
            log.warn("event=backpressure_engaged channel={} pendingBytes={}",
                    ctx.channel().id(), ctx.channel().bytesBeforeWritable());
        }
        ctx.fireChannelWritabilityChanged();
    }
}
