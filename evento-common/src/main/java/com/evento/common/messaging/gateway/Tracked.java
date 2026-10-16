package com.evento.common.messaging.gateway;

import com.evento.common.modeling.messaging.query.ConsistencyToken;

/**
 * A command's result together with where its event was stored. Pass {@link #token()} to
 * a later query ({@code QueryOptions#after}) to read this write, here or in another
 * process ({@link ConsistencyToken#encode()}).
 *
 * @param result the command's result, as {@code CommandGateway#send} returns it
 * @param token  where the command's event was stored; {@link ConsistencyToken#NONE} when it
 *               stored none or the broker did not say (an older broker)
 * @param <R>    the result type
 */
public record Tracked<R>(R result, ConsistencyToken token) {
    public Tracked {
        if (token == null) token = ConsistencyToken.NONE;
    }
}
