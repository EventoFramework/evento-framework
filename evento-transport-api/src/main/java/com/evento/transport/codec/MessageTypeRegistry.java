package com.evento.transport.codec;

import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Ping;
import com.evento.transport.message.Pong;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.StreamCancel;
import com.evento.transport.message.StreamCredit;
import com.evento.transport.message.StreamData;
import com.evento.transport.message.StreamRedirect;
import com.evento.transport.message.Welcome;

import java.util.Map;
import java.util.Set;

/**
 * Closed enumeration of wire-level message types. Acts as a whitelist for deserialization:
 * any class outside this set is rejected by the codec. Source of truth for the {@code @t}
 * discriminator names used by Jackson.
 */
public final class MessageTypeRegistry {

    private static final Map<String, Class<? extends Message>> BY_NAME = Map.ofEntries(
            Map.entry("HEL", Hello.class),
            Map.entry("WLC", Welcome.class),
            Map.entry("REJ", Reject.class),
            Map.entry("PNG", Ping.class),
            Map.entry("PON", Pong.class),
            Map.entry("REQ", Request.class),
            Map.entry("RSP", Response.class),
            Map.entry("NTF", Notification.class),
            // 3.0, only exchanged with peers that negotiated the stream capability
            Map.entry("SDA", StreamData.class),
            Map.entry("SCR", StreamCredit.class),
            Map.entry("SCN", StreamCancel.class),
            Map.entry("SRD", StreamRedirect.class)
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
