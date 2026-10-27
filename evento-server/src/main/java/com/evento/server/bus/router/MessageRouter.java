package com.evento.server.bus.router;

import com.evento.transport.Frame;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Pong;
import com.evento.transport.message.Request;
import com.evento.transport.message.Message;
import com.evento.transport.message.Response;
import com.evento.transport.message.StreamCancel;
import com.evento.transport.message.StreamCredit;
import com.evento.transport.message.StreamData;
import com.evento.transport.message.StreamRedirect;
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
    /** The frames of streamed replies (3.0): {@code StreamData}, {@code StreamCredit}, {@code StreamCancel}, {@code StreamRedirect}. */
    @FunctionalInterface public interface StreamHandler { void handle(Message message, Frame frame, BundleSession session); }

    private final HelloHandler helloHandler;
    private final RequestHandler requestHandler;
    private final ResponseHandler responseHandler;
    private final NotificationHandler notificationHandler;
    private final StreamHandler streamHandler;

    public MessageRouter(HelloHandler helloHandler,
                         RequestHandler requestHandler,
                         ResponseHandler responseHandler,
                         NotificationHandler notificationHandler) {
        this(helloHandler, requestHandler, responseHandler, notificationHandler, null);
    }

    /** @param streamHandler where stream frames go; null logs them as unrouted */
    public MessageRouter(HelloHandler helloHandler,
                         RequestHandler requestHandler,
                         ResponseHandler responseHandler,
                         NotificationHandler notificationHandler,
                         StreamHandler streamHandler) {
        this.helloHandler = helloHandler;
        this.requestHandler = requestHandler;
        this.responseHandler = responseHandler;
        this.notificationHandler = notificationHandler;
        this.streamHandler = streamHandler;
    }

    public void route(Frame frame, BundleSession session) {
        switch (frame.message()) {
            case Hello h -> helloHandler.handle(h, frame, session);
            case Request r -> requestHandler.handle(r, frame, session);
            case Response r -> responseHandler.handle(r, frame, session);
            case Notification n -> notificationHandler.handle(n, frame, session);
            case StreamData d when streamHandler != null -> streamHandler.handle(d, frame, session);
            case StreamCredit c when streamHandler != null -> streamHandler.handle(c, frame, session);
            case StreamCancel c when streamHandler != null -> streamHandler.handle(c, frame, session);
            case StreamRedirect r when streamHandler != null -> streamHandler.handle(r, frame, session);
            case Pong p -> log.trace("event=pong_received correlationId={} session={}", p.correlationId(),
                    session.address() == null ? "<accepted>" : session.address().instanceId());
            default -> log.warn("event=unrouted_message type={} session={}",
                    frame.message().getClass().getSimpleName(),
                    session.address() == null ? "<accepted>" : session.address().instanceId());
        }
    }
}
