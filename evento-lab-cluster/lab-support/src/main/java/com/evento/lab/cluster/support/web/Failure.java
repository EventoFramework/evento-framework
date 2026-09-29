package com.evento.lab.cluster.support.web;

import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Maps an Evento failure to an HTTP status and a stable {@code kind} the chaos
 * driver aggregates on.
 *
 * <p>Every failure of the command itself is a 500, never 502-504: the lab LB
 * treats 502-504 as a dead gateway and would take this region's gateway out
 * of rotation (and serve the region from its same-cell backup) because of
 * one failed command. 503 is reserved for "this gateway's bundle is not
 * connected yet", where failing over to a sibling gateway is right.
 *
 * <p>The kinds follow the log markers of the readiness assessment so a
 * scenario report can say "12 x no_handler" instead of quoting stack traces.
 *
 * @param status  HTTP status
 * @param kind    stable classifier: timeout | no_handler | no_connection | disconnected |
 *                broker_changed | rejected | not_found | error
 * @param message the innermost message
 * @param type    the innermost exception class
 */
public record Failure(int status, String kind, String message, String type) {

    public static Failure of(Throwable t) {
        var root = unwrap(t);
        var chain = describeChain(t).toLowerCase(Locale.ROOT);
        var msg = root.getMessage() == null ? root.toString() : root.getMessage();
        var type = root.getClass().getName();
        if (chain.contains("timeout") || chain.contains("timed out")) {
            return new Failure(500, "timeout", msg, type);
        }
        if (chain.contains("no handler for") || chain.contains("no available handler")) {
            return new Failure(500, "no_handler", msg, type);
        }
        if (chain.contains("no connection for")) {
            return new Failure(500, "no_connection", msg, type);
        }
        if (chain.contains("brokerchanged")) {
            return new Failure(500, "broker_changed", msg, type);
        }
        if (chain.contains("peer disconnected") || chain.contains("disconnected")
                || chain.contains("connection reset") || chain.contains("closed")
                || chain.contains("sendfailed") || chain.contains("not connected")) {
            return new Failure(500, "disconnected", msg, type);
        }
        if (chain.contains("aggregateinitialized") || chain.contains("already completed")
                || chain.contains("already initialized") || chain.contains("already exists")
                || chain.contains("aggregatenotinitialized")) {
            return new Failure(409, "rejected", msg, type);
        }
        if (chain.contains("nosuchelement") || chain.contains("not found")) {
            return new Failure(404, "not_found", msg, type);
        }
        return new Failure(500, "error", msg, type);
    }

    static Throwable unwrap(Throwable t) {
        var cur = t;
        while ((cur instanceof ExecutionException || cur instanceof CompletionException) && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    static String describeChain(Throwable t) {
        var sb = new StringBuilder();
        var cur = t;
        int depth = 0;
        while (cur != null && depth++ < 12) {
            sb.append(cur.getClass().getSimpleName()).append(':').append(cur.getMessage()).append(" | ");
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return sb.toString();
    }
}
