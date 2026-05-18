package com.evento.transport.codec;

import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Ping;
import com.evento.transport.message.Pong;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.Welcome;

import java.util.Map;
import java.util.Set;

/**
 * Closed enumeration of wire-level message types. Acts as a whitelist for deserialization:
 * any class outside this set is rejected by the codec. Source of truth for the {@code @t}
 * discriminator names used by Jackson.
 */
public final class MessageTypeRegistry {

    private static final Map<String, Class<? extends Message>> BY_NAME = Map.of(
            "HEL", Hello.class,
            "WLC", Welcome.class,
            "REJ", Reject.class,
            "PNG", Ping.class,
            "PON", Pong.class,
            "REQ", Request.class,
            "RSP", Response.class,
            "NTF", Notification.class
    );

    private static final Set<Class<? extends Message>> ALLOWED = Set.copyOf(BY_NAME.values());

    private MessageTypeRegistry() {}

    public static Set<Class<? extends Message>> allowed() {
        return ALLOWED;
    }

    public static Map<String, Class<? extends Message>> byName() {
        return BY_NAME;
    }

    public static boolean isAllowed(Class<?> type) {
        return ALLOWED.contains(type);
    }
}
