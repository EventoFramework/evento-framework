package com.evento.application.client;

import com.evento.application.client.handler.HandlerRegistry;
import com.evento.application.client.metrics.BundleLatencies;
import com.evento.application.client.metrics.LatencyRecorder;
import com.evento.application.manager.AggregateManager;
import com.evento.application.manager.ProjectionManager;
import com.evento.application.manager.ServiceManager;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.modeling.messaging.query.QueryStreamBatch;
import com.evento.common.modeling.messaging.query.Streamed;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Ports the v1 switch-on-body-type dispatch lambda
 * (see {@code EventoBundle.Builder.start()}, lines 589-642 pre-v2) into the
 * v2 {@link HandlerRegistry.RequestHandler} shape: byte[] in, byte[] out.
 *
 * <p>Decodes the inbound CBOR payload to a v1 {@code EventoRequest}, demuxes
 * by the wrapped body type to the right manager, wraps the manager's return
 * value in a v1 {@code EventoResponse}, and re-encodes for the wire. Failures
 * become {@code ExceptionWrapper} bodies — same shape v1 used, so caller-side
 * decode in {@link EventoServerAdapter#request} continues to work.
 *
 * <p>One dispatcher instance is registered against every command / query
 * payloadType the bundle declares — the routing decision was already made by
 * the v2 broker (it picked the bundle by payloadType), this class just runs
 * the right manager for the wrapper type.
 *
 * <p><b>Streamed queries (3.0).</b> A query asked as a stream runs through the same
 * {@code ProjectionManager} path (tracing agent, interceptors, performance metrics), then
 * its views are sent in batches sized to about {@link #setStreamFrameBytes frame bytes},
 * each batch only against the caller's credit. A {@code Streamed} answer is read from its
 * source as the batches go; any other answer is iterated. A failure of the query, before
 * or during the views, travels as an {@code ExceptionWrapper} frame, so the caller gets
 * the same exception a plain query would have thrown.
 */
public final class BundleInboundDispatcher implements HandlerRegistry.StreamingRequestHandler {

    /** Default target size of one stream frame. */
    public static final int DEFAULT_STREAM_FRAME_BYTES = 256 * 1024;

    private final AdminPayloadCodec codec;
    private final AggregateManager aggregateManager;
    private final ServiceManager serviceManager;
    private final ProjectionManager projectionManager;
    private final LatencyRecorder handlerLatency;
    /** Holds a query that must reflect some writes until the projectors have processed them. */
    private volatile com.evento.application.consumer.ConsistencyGate consistencyGate =
            com.evento.application.consumer.ConsistencyGate.NONE;
    private volatile int streamFrameBytes = DEFAULT_STREAM_FRAME_BYTES;
    /** Turns payloads of an older shape (sent by an older caller, or stored) into the current one. */
    private volatile com.evento.common.versioning.Upcasters upcasters = com.evento.common.versioning.Upcasters.NONE;
    private volatile RevisionGuard revisionGuard = RevisionGuard.NONE;

    /**
     * Upcasters applied to every request on arrival (3.0): the command or query, and for an
     * aggregate command the events and the snapshot it is rebuilt from.
     */
    public void setUpcasters(com.evento.common.versioning.Upcasters upcasters) {
        this.upcasters = upcasters == null ? com.evento.common.versioning.Upcasters.NONE : upcasters;
    }

    /** Refuses replies the broker of the session could not store with their revision (3.0). */
    public void setRevisionGuard(RevisionGuard guard) {
        this.revisionGuard = guard == null ? RevisionGuard.NONE : guard;
    }

    /** Target size of one frame of a streamed answer (views per batch adapt to it). */
    public void setStreamFrameBytes(int bytes) {
        this.streamFrameBytes = Math.max(1024, bytes);
    }

    public BundleInboundDispatcher(AdminPayloadCodec codec,
                                   AggregateManager aggregateManager,
                                   ServiceManager serviceManager,
                                   ProjectionManager projectionManager) {
        this(codec, aggregateManager, serviceManager, projectionManager, null);
    }

    /**
     * @param handlerLatency where to record each handler's execution time (by handler type, see
     *                       {@link BundleLatencies#handlers()}); {@code null} records nothing
     */
    public BundleInboundDispatcher(AdminPayloadCodec codec,
                                   AggregateManager aggregateManager,
                                   ServiceManager serviceManager,
                                   ProjectionManager projectionManager,
                                   LatencyRecorder handlerLatency) {
        this.codec = codec;
        this.aggregateManager = aggregateManager;
        this.serviceManager = serviceManager;
        this.projectionManager = projectionManager;
        this.handlerLatency = handlerLatency;
    }

    /** Read-your-writes for the queries this bundle answers (see {@code QueryMessage#getConsistency()}). */
    public void setConsistencyGate(com.evento.application.consumer.ConsistencyGate gate) {
        this.consistencyGate = gate == null ? com.evento.application.consumer.ConsistencyGate.NONE : gate;
    }

    @Override
    public byte[] handle(byte[] payload, HandlerRegistry.RequestContext context) {
        long start = System.nanoTime();
        var request = codec.decodeRequest(payload);
        long deadlineNanos = start + consistencyBudgetNanos(request);
        long decoded = System.nanoTime();
        var reply = new EventoResponse();
        reply.setCorrelationId(request.getCorrelationId());
        reply.setRequestTimestamp(request.getTimestamp());
        reply.setTimeout(request.getTimeout());
        reply.setUnit(request.getUnit());
        try {
            upcast(request.getBody());
            var result = dispatch(request.getBody(), deadlineNanos);
            revisionGuard.checkReply(result);
            reply.setBody(result);
        } catch (Throwable t) {
            reply.setBody(new ExceptionWrapper(t));
        }
        long handled = System.nanoTime();
        byte[] encoded = codec.encodeResponse(reply);
        if (handlerLatency != null) {
            String type = handlerType(request.getBody());
            if (type != null) {
                handlerLatency.record(type, handled - decoded);
                handlerLatency.record(type + ".decode", decoded - start);
                handlerLatency.record(type + ".encode", System.nanoTime() - handled);
            }
        }
        return encoded;
    }

    @Override
    public void handleStream(byte[] payload, HandlerRegistry.RequestContext context,
                             HandlerRegistry.StreamSink sink) throws Exception {
        long start = System.nanoTime();
        var request = codec.decodeRequest(payload);
        if (!(request.getBody() instanceof QueryMessage<?> qm)) {
            // Only queries stream; anything else answers whole, as one frame.
            sink.send(handle(payload, context));
            return;
        }
        long decoded = System.nanoTime();
        QueryResponse<?> result;
        try {
            upcast(qm);
            if (qm.getConsistency() != null) {
                awaitConsistency(qm, start + consistencyBudgetNanos(request));
            }
            result = projectionManager.handle(qm);
        } catch (Throwable t) {
            sink.send(frame(request, new ExceptionWrapper(t)));
            return;
        }
        long handled = System.nanoTime();
        if (handlerLatency != null) {
            handlerLatency.record(BundleLatencies.QUERY, handled - decoded);
            handlerLatency.record(BundleLatencies.QUERY + ".decode", decoded - start);
        }
        long views = 0;
        long frames = 0;
        try (var stream = QueryStreamBatch.views(result)) {
            var it = stream.iterator();
            int batchSize = 32;
            var batch = new ArrayList<View>(batchSize);
            while (it.hasNext()) {
                batch.add(it.next());
                if (batch.size() >= batchSize) {
                    var bytes = frame(request, new QueryStreamBatch(batch));
                    sink.send(bytes);
                    views += batch.size();
                    frames++;
                    batchSize = nextBatchSize(batchSize, bytes.length);
                    batch = new ArrayList<>(batchSize);
                }
            }
            if (!batch.isEmpty()) {
                sink.send(frame(request, new QueryStreamBatch(batch)));
                views += batch.size();
                frames++;
            }
        } catch (HandlerRegistry.StreamCancelledException | InterruptedException stop) {
            throw stop;
        } catch (Throwable t) {
            // The source failed mid-way: the caller gets the views so far, then this.
            sink.send(frame(request, new ExceptionWrapper(t)));
        }
        if (handlerLatency != null) {
            handlerLatency.record(BundleLatencies.QUERY + ".stream", System.nanoTime() - handled);
        }
        if (log.isDebugEnabled()) {
            log.debug("event=query_streamed query={} views={} frames={} elapsedMs={}", qm.getQueryName(), views, frames,
                    (System.nanoTime() - start) / 1_000_000.0);
        }
    }

    /** Views per batch so that a frame is about {@link #streamFrameBytes}. */
    private int nextBatchSize(int current, int frameBytes) {
        int target = streamFrameBytes;
        if (frameBytes > target + target / 2) return Math.max(1, current / 2);
        if (frameBytes < target / 2) return Math.min(8192, current * 2);
        return current;
    }

    private byte[] frame(com.evento.common.modeling.messaging.message.internal.EventoRequest request, Serializable body) {
        var reply = new EventoResponse();
        reply.setCorrelationId(request.getCorrelationId());
        reply.setRequestTimestamp(request.getTimestamp());
        reply.setTimeout(request.getTimeout());
        reply.setUnit(request.getUnit());
        reply.setBody(body);
        return codec.encodeResponse(reply);
    }

    /**
     * Brings a request to the shapes this bundle's classes have. An aggregate command carries
     * the aggregate's history too: its events and snapshot are upcast before the aggregate is
     * rebuilt from them, like the events a projector reads.
     */
    private void upcast(Object body) {
        var chain = upcasters;
        if (chain.isEmpty()) return;
        switch (body) {
            case DecoratedDomainCommandMessage cm -> {
                chain.upcast(cm.getCommandMessage());
                if (cm.getEventStream() != null) cm.getEventStream().forEach(chain::upcast);
                chain.upcast(cm.getSerializedAggregateState());
            }
            case com.evento.common.modeling.messaging.message.application.Message<?> m -> chain.upcast(m);
            case null, default -> { }
        }
    }

    private static String handlerType(Object body) {
        return switch (body) {
            case DecoratedDomainCommandMessage ignored -> BundleLatencies.AGGREGATE;
            case ServiceCommandMessage ignored -> BundleLatencies.SERVICE;
            case QueryMessage<?> ignored -> BundleLatencies.QUERY;
            case null, default -> null;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Serializable dispatch(Object body, long deadlineNanos) throws Throwable {
        if (body instanceof QueryMessage<?> qm && qm.getConsistency() != null) {
            awaitConsistency(qm, deadlineNanos);
        }
        return switch (body) {
            case DecoratedDomainCommandMessage cm -> aggregateManager.handle(cm);
            case ServiceCommandMessage sm -> serviceManager.handle(sm);
            // A plain reply carries every view: a lazy Streamed is read to the end here.
            case QueryMessage qm -> materialized(projectionManager.handle(qm));
            case null -> throw new IllegalArgumentException("inbound request body is null");
            default -> throw new IllegalArgumentException(
                    "unsupported inbound request body type: " + body.getClass().getName());
        };
    }

    /** A lazy {@link Streamed} read to the end; any other result as it is. */
    static Serializable materialized(QueryResponse<?> result) {
        return result instanceof Streamed<?> streamed ? streamed.materialize() : result;
    }

    /**
     * How long a query may wait for its writes: the caller's timeout less a margin for the
     * way back (a tenth, at most 250 ms), so the caller gets the explicit
     * {@code ConsistencyTimeoutException} rather than its own indeterminate timeout.
     */
    static long consistencyBudgetNanos(com.evento.common.modeling.messaging.message.internal.EventoRequest request) {
        if (request.getUnit() == null || request.getTimeout() <= 0) return java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        long timeout = request.getUnit().toNanos(request.getTimeout());
        return timeout - Math.min(timeout / 10, java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(250));
    }

    private void awaitConsistency(QueryMessage<?> qm, long deadlineNanos) throws InterruptedException {
        long waited = consistencyGate.await(qm.getConsistency(), deadlineNanos);
        if (waited > 0 && log.isDebugEnabled()) {
            log.debug("event=query_waited_for_writes query={} waitedMs={} token={}",
                    qm.getQueryName(), waited / 1_000_000.0, qm.getConsistency().encode());
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BundleInboundDispatcher.class);
}
