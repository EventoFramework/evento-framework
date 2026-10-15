package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * The handler of a streamed reply offers it on a direct connection instead of through
 * the broker (2.6), handler → caller, relayed by the broker as is. Sent only to a caller
 * that asked for it ({@link Request#streamDirect()}).
 *
 * <p>The caller connects to {@code host:port} and presents {@code ticket} in its
 * {@link Hello} ({@code authToken}), with the stream's {@code correlationId}. The ticket
 * is random, single-use and short-lived; it names this stream and nothing else. The
 * stream then runs on that connection exactly as through the broker ({@link StreamData},
 * {@link StreamCredit}, {@link StreamCancel}). A caller that cannot connect asks again
 * through the broker; the handler drops the unused ticket when it expires.
 */
public record StreamRedirect(
        UUID correlationId,
        String host,
        int port,
        String ticket,
        long expiresAtMs,
        long timestampMs
) implements Message {

    @JsonCreator
    public StreamRedirect(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("ticket") String ticket,
            @JsonProperty("expiresAtMs") long expiresAtMs,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.host = host;
        this.port = port;
        this.ticket = ticket;
        this.expiresAtMs = expiresAtMs;
        this.timestampMs = timestampMs;
    }
}
