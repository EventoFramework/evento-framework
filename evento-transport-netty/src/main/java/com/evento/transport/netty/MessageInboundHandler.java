package com.evento.transport.netty;

import com.evento.transport.Frame;
import com.evento.transport.state.ConnectionState;
import com.evento.transport.state.ConnectionStateMachine;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Terminal handler: forwards each inbound {@link Frame} (parsed {@code Message}
 * + raw bytes) to the application listener on a business {@link Executor}
 * (virtual threads by default) so the event loop is never blocked by user logic.
 *
 * <p>Also tracks the last-inbound timestamp for failure detection at the
 * application layer.
 */
final class MessageInboundHandler extends SimpleChannelInboundHandler<Frame> {

    private static final Logger log = LoggerFactory.getLogger(MessageInboundHandler.class);

    private final Consumer<Frame> listener;
    private final Executor businessExecutor;
    private final AtomicLong lastInboundMs;
    private final ConnectionStateMachine state;

    MessageInboundHandler(Consumer<Frame> listener,
                          Executor businessExecutor,
                          AtomicLong lastInboundMs,
                          ConnectionStateMachine state) {
        this.listener = listener;
        this.businessExecutor = businessExecutor;
        this.lastInboundMs = lastInboundMs;
        this.state = state;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Frame frame) {
        lastInboundMs.set(System.currentTimeMillis());
        businessExecutor.execute(() -> {
            try {
                listener.accept(frame);
            } catch (Throwable t) {
                log.error("event=listener_error channel={} type={} correlationId={}",
                        ctx.channel().id(),
                        frame.message().getClass().getSimpleName(),
                        frame.message().correlationId(), t);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("event=channel_exception channel={}", ctx.channel().id(), cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("event=channel_inactive channel={}", ctx.channel().id());
        // Force state machine forward so the surrounding Transport notifies its listeners.
        if (state.current() != ConnectionState.CLOSED) {
            state.forceTransition(ConnectionState.DISCONNECTED, "channel_inactive");
        }
        ctx.fireChannelInactive();
    }
}
