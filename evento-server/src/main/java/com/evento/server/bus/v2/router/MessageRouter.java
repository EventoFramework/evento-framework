package com.evento.server.bus.v2.router;

import com.evento.transport.MessageDispatcher;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-session inbound message dispatcher. Replaces v1's
 * {@code MessageBus.handleRequest()} + {@code handleMessage()} switches with a
 * registry-based dispatcher (one entry per wire type) so adding a new message
 * type means registering a handler at startup — no existing switch to grow.
 *
 * <p>Constructor takes the four handler callbacks (one per message variant);
 * the {@link MessageDispatcher} from {@code evento-transport-api} performs the
 * actual lookup. The session is passed as the dispatch context so handlers see
 * who sent each message.
 */
public final class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    @FunctionalInterface public interface HelloHandler { void handle(Hello hello, BundleSession session); }
    @FunctionalInterface public interface RequestHandler { void handle(Request request, BundleSession session); }
    @FunctionalInterface public interface ResponseHandler { void handle(Response response, BundleSession session); }
    @FunctionalInterface public interface NotificationHandler { void handle(Notification notification, BundleSession session); }

    private final MessageDispatcher<BundleSession> dispatcher;

    public MessageRouter(HelloHandler helloHandler,
                         RequestHandler requestHandler,
                         ResponseHandler responseHandler,
                         NotificationHandler notificationHandler) {
        this.dispatcher = new MessageDispatcher<BundleSession>()
                .register(Hello.class, helloHandler::handle)
                .register(Request.class, requestHandler::handle)
                .register(Response.class, responseHandler::handle)
                .register(Notification.class, notificationHandler::handle)
                .onUnhandled((m, s) -> log.warn("event=unrouted_message type={} session={}",
                        m.getClass().getSimpleName(),
                        s.address() == null ? "<accepted>" : s.address().instanceId()));
    }

    public void route(Message message, BundleSession session) {
        dispatcher.dispatch(message, session);
    }
}
