package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Wire representation of an error response. Carries class name + message + stack trace
 * as strings (no Java serialization of Throwable, no class loading concerns).
 */
public record ResponseError(
        String exceptionClassName,
        String message,
        String stackTrace
) {

    @JsonCreator
    public ResponseError(
            @JsonProperty("exceptionClassName") String exceptionClassName,
            @JsonProperty("message") String message,
            @JsonProperty("stackTrace") String stackTrace
    ) {
        this.exceptionClassName = exceptionClassName;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    public static ResponseError of(Throwable t) {
        if (t == null) return null;
        var sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return new ResponseError(t.getClass().getName(), t.getMessage(), sw.toString());
    }
}
