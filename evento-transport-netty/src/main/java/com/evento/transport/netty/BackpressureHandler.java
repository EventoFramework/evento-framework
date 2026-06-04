package com.evento.transport.netty;

import com.evento.transport.state.ConnectionState;
import com.evento.transport.state.ConnectionStateMachine;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reflects Netty's per-channel writability into the {@link ConnectionStateMachine}
 * with <em>temporal hysteresis</em>, so that the sub-millisecond TCP write-buffer
 * oscillations around the high/low water marks do not cause a storm of
 * {@code CONNECTED ↔ DEGRADED} transitions.
 *
 * <p>Design:
 * <ul>
 *   <li>When the channel becomes <em>unwritable</em>, we schedule a "degrade"
 *       task to fire after {@code degradeAfter}. If writability returns first,
 *       the task is cancelled and no state transition is emitted.</li>
 *   <li>When the channel becomes <em>writable</em>, we schedule a "recover"
 *       task to fire after {@code recoverAfter} (asymmetric, longer than
 *       degrade by default). If unwritability returns first, the task is
 *       cancelled.</li>
 *   <li>Per-flap events are logged at {@code DEBUG} only. Actual state
 *       transitions emitted by the {@link ConnectionStateMachine} remain at
 *       {@code INFO}.</li>
 * </ul>
 *
 * <p>The handler is purely a state mirror; it does not block writes.
 */
final class BackpressureHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(BackpressureHandler.class);

    /** Default time the channel must stay unwritable before we transition to DEGRADED. */
    static final Duration DEFAULT_DEGRADE_AFTER = Duration.ofMillis(500);
    /** Default time the channel must stay writable before we transition back to CONNECTED. */
    static final Duration DEFAULT_RECOVER_AFTER = Duration.ofSeconds(1);

    private final ConnectionStateMachine state;
    private final long degradeAfterNanos;
    private final long recoverAfterNanos;

    // Per-channel mutable state. Handler is added per-pipeline (one instance per channel),
    // and all callbacks run on that channel's event loop, so no synchronization is needed
    // beyond confining access to channelWritabilityChanged / the scheduled task.
    private ScheduledFuture<?> pendingDegrade;
    private ScheduledFuture<?> pendingRecover;
    private final AtomicLong flapCount = new AtomicLong();

    BackpressureHandler(ConnectionStateMachine state) {
        this(state, DEFAULT_DEGRADE_AFTER, DEFAULT_RECOVER_AFTER);
    }

    BackpressureHandler(ConnectionStateMachine state, Duration degradeAfter, Duration recoverAfter) {
        this.state = state;
        this.degradeAfterNanos = Math.max(0L, degradeAfter.toNanos());
        this.recoverAfterNanos = Math.max(degradeAfterNanos, recoverAfter.toNanos());
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        boolean writable = ctx.channel().isWritable();
        flapCount.incrementAndGet();

        if (writable) {
            // Writable again: cancel any pending degrade, schedule recovery.
            cancel(pendingDegrade);
            pendingDegrade = null;
            if (log.isDebugEnabled()) {
                log.debug("event=backpressure_writable channel={} pendingBytes={}",
                        ctx.channel().id(), ctx.channel().bytesBeforeWritable());
            }
            if (pendingRecover == null || pendingRecover.isDone()) {
                pendingRecover = ctx.executor().schedule(() -> {
                    pendingRecover = null;
                    state.compareAndTransition(ConnectionState.DEGRADED, ConnectionState.CONNECTED,
                            "channel_writable_recovered");
                }, recoverAfterNanos, TimeUnit.NANOSECONDS);
            }
        } else {
            // Unwritable: cancel any pending recovery, schedule a degrade.
            cancel(pendingRecover);
            pendingRecover = null;
            if (log.isDebugEnabled()) {
                log.debug("event=backpressure_engaged channel={} pendingBytes={}",
                        ctx.channel().id(), ctx.channel().bytesBeforeWritable());
            }
            if (pendingDegrade == null || pendingDegrade.isDone()) {
                pendingDegrade = ctx.executor().schedule(() -> {
                    pendingDegrade = null;
                    boolean transitioned = state.compareAndTransition(
                            ConnectionState.CONNECTED, ConnectionState.DEGRADED,
                            "channel_unwritable_backpressure");
                    if (transitioned) {
                        log.warn("event=backpressure_sustained channel={} pendingBytes={} flapsSinceLast={}",
                                ctx.channel().id(), ctx.channel().bytesBeforeWritable(),
                                flapCount.getAndSet(0));
                    }
                }, degradeAfterNanos, TimeUnit.NANOSECONDS);
            }
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancel(pendingDegrade);
        cancel(pendingRecover);
        pendingDegrade = null;
        pendingRecover = null;
    }

    private static void cancel(ScheduledFuture<?> f) {
        if (f != null && !f.isDone()) {
            f.cancel(false);
        }
    }
}
