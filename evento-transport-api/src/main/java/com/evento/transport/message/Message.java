package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

/**
 * Wire-level message sealed hierarchy.
 *
 * <p>The codec encodes the concrete type via a short discriminator (the {@code @t} field),
 * mapped through {@link JsonSubTypes}. Adding a new message type requires:
 * <ol>
 *   <li>adding it to the {@code permits} clause,</li>
 *   <li>adding a {@link JsonSubTypes.Type} entry below, and</li>
 *   <li>registering a handler in the {@code MessageDispatcher} at startup.</li>
 * </ol>
 *
 * <p>The whitelist enforced by {@code MessageTypeRegistry} prevents arbitrary class
 * instantiation at deserialization time.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@t", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Hello.class,        name = "HEL"),
        @JsonSubTypes.Type(value = Welcome.class,      name = "WLC"),
        @JsonSubTypes.Type(value = Reject.class,       name = "REJ"),
        @JsonSubTypes.Type(value = Ping.class,         name = "PNG"),
        @JsonSubTypes.Type(value = Pong.class,         name = "PON"),
        @JsonSubTypes.Type(value = Request.class,      name = "REQ"),
        @JsonSubTypes.Type(value = Response.class,     name = "RSP"),
        @JsonSubTypes.Type(value = Notification.class, name = "NTF"),
})
public sealed interface Message permits Hello, Welcome, Reject, Ping, Pong, Request, Response, Notification {

    /**
     * Correlation identifier. For Request/Response pairs, this is the shared ID;
     * for one-way messages, it is a random ID useful for tracing.
     */
    UUID correlationId();
}
