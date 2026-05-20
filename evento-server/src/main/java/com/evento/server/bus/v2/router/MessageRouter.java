package com.evento.server.bus.v2.router;

import com.evento.transport.Frame;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-session inbound dispatcher. Replaces v1's
 * {@code MessageBus.handleRequest()} + {@code handleMessage()} switches with a
 * sealed pattern-match so adding a new wire type fails to compile until every
 * router site acknowledges it.
 *
 * <p>Handlers receive the full {@link Frame} (parsed message + raw bytes) so
 * forwarding paths can write the original bytes back out via {@code sendRaw}
 * — saving a CBOR re-encode on every relayed Request/Response.
 */
public final class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    @FunctionalInterface public interface HelloHandler { void handle(Hello hello, Frame frame, BundleSession session); }
    @FunctionalInterface public interface RequestHandler { void handle(Request request, Frame frame, BundleSession session); }
    @FunctionalInterface public interface ResponseHandler { void handle(Response response, Frame frame, BundleSession session); }
    @FunctionalInterface public interface NotificationHandler { void handle(Notification notification, Frame frame, BundleSession session); }

    private final HelloHandler helloHandler;
    private final RequestHandler requestHandler;
    private final ResponseHandler responseHandler;
    private final NotificationHandler notificationHandler;

    public MessageRouter(HelloHandler helloHandler,
                         RequestHandler requestHandler,
                         ResponseHandler responseHandler,
                         NotificationHandler notificationHandler) {
        this.helloHandler = helloHandler;
        this.requestHandler = requestHandler;
        this.responseHandler = responseHandler;
        this.notificationHandler = notificationHandler;
    }

    public void route(Frame frame, BundleSession session) {
        switch (frame.message()) {
            case Hello h -> helloHandler.handle(h, frame, session);
            case Request r -> requestHandler.handle(r, frame, session);
            case Response r -> responseHandler.handle(r, frame, session);
            case Notification n -> notificationHandler.handle(n, frame, session);
            default -> log.warn("event=unrouted_message type={} session={}",
                    frame.message().getClass().getSimpleName(),
                    session.address() == null ? "<accepted>" : session.address().instanceId());
        }
    }
}
