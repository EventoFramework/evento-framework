package com.evento.application.client.metrics;

/**
 * The latencies a bundle measures itself (bundles have no Micrometer; publish these with
 * whatever the application uses, e.g. a gauge per percentile):
 *
 * <ul>
 *   <li>{@link #requestRtt()}, by payload type: the round trip of every request this bundle sent
 *       and got a non-error answer for, from just before the frame is handed to the transport to
 *       the answer's frame decoded (it excludes the caller-side {@code EventoRequest} encode and
 *       {@code EventoResponse} decode). For an aggregate command this is the whole broker path
 *       (lock, story, forward to the aggregate bundle, append, reply) plus the network to the
 *       broker and back.</li>
 *   <li>{@link #handlers()}, by handler type ({@code aggregate}, {@code service}, {@code query}):
 *       how long the handler ran in this bundle, the decode of the inbound request excluded;
 *       {@code <type>.decode} and {@code <type>.encode} are the CBOR decode of the request and
 *       encode of the answer around it. An aggregate handler's time includes replaying the
 *       story it was sent (event-sourcing handlers) before the command handler itself.</li>
 * </ul>
 */
public final class BundleLatencies {

    /** Handler type of an aggregate command handler. */
    public static final String AGGREGATE = "aggregate";
    /** Handler type of a service command handler. */
    public static final String SERVICE = "service";
    /** Handler type of a query handler. */
    public static final String QUERY = "query";

    private final LatencyRecorder requestRtt = new LatencyRecorder(512);
    private final LatencyRecorder handlers = new LatencyRecorder(16);

    public LatencyRecorder requestRtt() {
        return requestRtt;
    }

    public LatencyRecorder handlers() {
        return handlers;
    }

    /** Starts a new window on every histogram. */
    public void reset() {
        requestRtt.reset();
        handlers.reset();
    }
}
