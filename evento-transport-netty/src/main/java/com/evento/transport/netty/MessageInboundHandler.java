package com.evento.transport.netty;

import com.evento.transport.Frame;
import com.evento.transport.message.Response;
import com.evento.transport.message.StreamData;
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
 * <p>{@link Response}s do not go to the business executor: each runs on a virtual thread of its
 * own ({@link #RESPONSES}). A request handler that waits for the response to a request of its
 * own (the broker forwarding a command; a bundle handler sending a nested command) holds a
 * business thread while it waits. With a bounded business executor (the server's: 8 threads per
 * CPU) and as many such handlers in flight as threads, the responses that would release them
 * were queued behind them and never ran: every command waited until it expired. Responses need
 * no bound of their own: each answers a request this side sent, so there are never more than
 * the requests in flight.
 *
 * <p>Also tracks the last-inbound timestamp for failure detection at the
 * application layer, and holds the disconnect transition until the responses already
 * read have reached the listener ({@link InboundDeliveries}).
 */
final class MessageInboundHandler extends SimpleChannelInboundHandler<Frame> {

    private static final Logger log = LoggerFactory.getLogger(MessageInboundHandler.class);

    /** Where responses run: never behind the requests that wait for them. */
    private static final Executor RESPONSES = java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("evento-response-", 0).factory());

    private final Consumer<Frame> listener;
    private final Executor businessExecutor;
    private final AtomicLong lastInboundMs;
    private final ConnectionStateMachine state;
    /** The frames of streamed replies of this channel, in the order they were read. */
    private final SerialLane streamLane;

    MessageInboundHandler(Consumer<Frame> listener,
                          Executor businessExecutor,
                          AtomicLong lastInboundMs,
                          ConnectionStateMachine state) {
        this.listener = listener;
        this.businessExecutor = businessExecutor;
        this.lastInboundMs = lastInboundMs;
        this.state = state;
        this.streamLane = new SerialLane(businessExecutor);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Frame frame) {
        lastInboundMs.set(System.currentTimeMillis());
        var deliveries = frame.message() instanceof Response ? InboundDeliveries.of(ctx.channel()) : null;
        if (deliveries != null) deliveries.begin();
        // A stream's frames must keep their order: one lane per channel. Everything else
        // runs concurrently, each frame on its own thread; responses off the business executor.
        Executor executor = frame.message() instanceof StreamData ? streamLane
                : deliveries != null ? RESPONSES : businessExecutor;
        try {
            executor.execute(() -> {
                try {
                    listener.accept(frame);
                } catch (Throwable t) {
                    log.error("event=listener_error channel={} type={} correlationId={}",
                            ctx.channel().id(),
                            frame.message().getClass().getSimpleName(),
                            frame.message().correlationId(), t);
                } finally {
                    if (deliveries != null) deliveries.end();
                }
            });
        } catch (RuntimeException e) {
            if (deliveries != null) deliveries.end();
            throw e;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("event=channel_exception channel={}", ctx.channel().id(), cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("event=channel_inactive channel={}", ctx.channel().id());
        // Force state machine forward so the surrounding Transport notifies its listeners,
        // once the responses already read have been delivered.
        InboundDeliveries.of(ctx.channel()).runWhenIdle(ctx.channel(), () -> {
            if (state.current() != ConnectionState.CLOSED) {
                state.forceTransition(ConnectionState.DISCONNECTED, "channel_inactive");
            }
        });
        ctx.fireChannelInactive();
    }
}
