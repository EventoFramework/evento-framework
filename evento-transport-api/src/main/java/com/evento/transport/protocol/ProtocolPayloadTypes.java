package com.evento.transport.protocol;

/**
 * Reserved {@code payloadType} strings on the {@code evento:*} namespace
 * carried by {@link com.evento.transport.message.Request} /
 * {@link com.evento.transport.message.Response}. Distinct from
 * {@link ProtocolNotifications} (which is for {@code Notification}-shaped
 * traffic).
 *
 * <p>Application code MUST NOT register handlers under these names.
 */
public final class ProtocolPayloadTypes {

    /**
     * Server-initiated admin request whose body is a CBOR-encoded v1
     * {@code com.evento.common.modeling.messaging.message.internal.EventoRequest}.
     * Used by {@code BusFacade.forward(...)} on the v2 path so the existing
     * dashboard / consumer endpoints keep working without each request body
     * type needing its own dedicated payloadType.
     *
     * <p>Bundles receive matching {@link com.evento.transport.message.Response}
     * carrying a CBOR-encoded {@code EventoResponse} as the payload.
     */
    public static final String SERVER_ADMIN_REQUEST = "evento:server-admin-request";

    /**
     * A bundle's barrier (3.0): the server answers it, with an empty success, only after it has
     * handled every notification the bundle sent before it on the same connection (the transport
     * queues it behind them). A bundle sends it after its registration, discovery and enable, and
     * after a later enable, so that it reports itself ready only when the server routes to it and
     * handles its commands. A 2.5 server answers it with "no handler", which the bundle accepts.
     */
    public static final String BARRIER = "evento:barrier";

    /**
     * Fire-and-forget admin notification from a bundle to the server, payload
     * is a CBOR-encoded v1
     * {@code com.evento.common.modeling.messaging.message.internal.EventoMessage}.
     * Used by {@code EventoServerAdapter.send(...)} so the existing
     * performance-metric / consumer-registration flows keep working without
     * the framework needing a dedicated payloadType per message body.
     *
     * <p>The server-side {@code BundleAdminNotificationListener} subscribes to
     * the {@link com.evento.server.bus.event.BusEvent.AdminNotification}
     * event stream, decodes the inner {@code EventoMessage}, and dispatches
     * its body to {@code PerformanceStoreService} / {@code ConsumerService}.
     */
    public static final String BUNDLE_ADMIN_NOTIFICATION = "evento:bundle-admin-notification";

    private ProtocolPayloadTypes() {}
}
