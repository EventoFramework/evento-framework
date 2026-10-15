package com.evento.common.modeling.exceptions;

/**
 * A query asked to reflect some writes ({@code ConsistencyToken}) and the answering
 * bundle's projectors did not process them before the query's deadline. Nothing was
 * read: the caller may retry (the projectors keep catching up), or ask again without
 * the requirement and accept a possibly stale answer.
 */
public class ConsistencyTimeoutException extends RuntimeException {

    public ConsistencyTimeoutException(String message) {
        super(message);
    }

    public ConsistencyTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
