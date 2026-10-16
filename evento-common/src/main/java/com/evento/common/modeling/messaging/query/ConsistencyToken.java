package com.evento.common.modeling.messaging.query;

import com.evento.common.utils.Context;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * "Reflect at least these writes": for each event context, the global sequence number
 * of the last event the caller wrote (or saw) there. A query carrying it is answered only
 * once the answering bundle's projectors have processed those events (read-your-writes),
 * or fails with {@code ConsistencyTimeoutException} when they do not catch up in time.
 *
 * <p>Positions are kept per context because a projector reads only its contexts: a
 * projector of context {@code EU} never sees an event of context {@code US}, and a single
 * number could make it wait for a sequence it will never reach. A projector reading every
 * context ({@link Context#WILDCARD}) waits for the highest position of the token.
 *
 * <p>Obtained from a command ({@code CommandGateway#sendTracked}), from everything this
 * bundle wrote ({@code QueryOptions#readYourWrites()}), or from another process through
 * {@link #encode()} / {@link #parse(String)} (an HTTP header, say). Immutable; tokens
 * combine with {@link #merge}.
 */
public final class ConsistencyToken implements Serializable {

    /** No requirement: the query is answered with whatever the read model holds. */
    public static final ConsistencyToken NONE = new ConsistencyToken(new TreeMap<>());

    private TreeMap<String, Long> positions;

    /** For the codec; an empty token, like {@link #NONE}. */
    public ConsistencyToken() {
        this.positions = new TreeMap<>();
    }

    private ConsistencyToken(TreeMap<String, Long> positions) {
        this.positions = positions;
    }

    /** At least event {@code sequenceNumber} of {@code context} (null = the default context). */
    public static ConsistencyToken of(String context, long sequenceNumber) {
        if (sequenceNumber < 0) return NONE;
        var map = new TreeMap<String, Long>();
        map.put(context == null ? Context.DEFAULT : context, sequenceNumber);
        return new ConsistencyToken(map);
    }

    /** The token that requires both this one and {@code other}: the highest position per context. */
    public ConsistencyToken merge(ConsistencyToken other) {
        if (other == null || other.isEmpty()) return this;
        if (isEmpty()) return other;
        var map = new TreeMap<>(positions);
        other.positions.forEach((context, seq) -> map.merge(context, seq, Math::max));
        return new ConsistencyToken(map);
    }

    /** Context → lowest sequence number a projector of that context must have processed. */
    public Map<String, Long> positions() {
        return Collections.unmodifiableMap(positions);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return positions == null || positions.isEmpty();
    }

    /**
     * What a projector reading {@code context} must have processed: the position of that
     * context, the highest of all for the wildcard, {@code -1} when nothing is required of it.
     */
    public long requiredFor(String context) {
        if (isEmpty()) return -1;
        if (context == null || Context.WILDCARD.equals(context)) {
            return positions.values().stream().mapToLong(Long::longValue).max().orElse(-1);
        }
        return positions.getOrDefault(context, -1L);
    }

    /** A compact, URL-safe text form: {@code context:sequence} pairs separated by commas. */
    public String encode() {
        var out = new StringBuilder();
        positions.forEach((context, seq) -> {
            if (!out.isEmpty()) out.append(',');
            out.append(URLEncoder.encode(context, StandardCharsets.UTF_8)).append(':').append(seq);
        });
        return out.toString();
    }

    /** The token {@link #encode()} produced; {@link #NONE} for null or blank text. */
    public static ConsistencyToken parse(String text) {
        if (text == null || text.isBlank()) return NONE;
        var map = new TreeMap<String, Long>();
        for (var pair : text.split(",")) {
            int colon = pair.lastIndexOf(':');
            if (colon <= 0 || colon == pair.length() - 1) {
                throw new IllegalArgumentException("malformed consistency token: " + text);
            }
            var context = URLDecoder.decode(pair.substring(0, colon), StandardCharsets.UTF_8);
            long seq;
            try {
                seq = Long.parseLong(pair.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed consistency token: " + text, e);
            }
            map.merge(context, seq, Math::max);
        }
        return new ConsistencyToken(map);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConsistencyToken that && Objects.equals(positions, that.positions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(positions);
    }

    @Override
    public String toString() {
        return "ConsistencyToken{" + encode() + '}';
    }
}
