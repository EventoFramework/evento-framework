package com.evento.test;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A {@link CommandGateway} that sends nothing: it records every command and answers with the
 * result stubbed for its class ({@link #answer}), or {@code null}.
 *
 * <p>The fixtures pass it to handlers; it can also be used on its own to test a component that
 * takes a {@code CommandGateway}.
 */
public final class RecordingCommandGateway implements CommandGateway {

    private final List<Command> sent = new ArrayList<>();
    private final Map<Class<?>, Function<Object, Object>> answers = new HashMap<>();

    /**
     * Answer commands of {@code type} with what {@code answer} returns; throw from it to make the
     * send fail (the future completes exceptionally).
     */
    @SuppressWarnings("unchecked")
    public <C extends Command> RecordingCommandGateway answer(Class<C> type, Function<C, ?> answer) {
        answers.put(type, c -> answer.apply((C) c));
        return this;
    }

    /** The commands sent so far, in order. */
    public List<Command> sent() {
        return List.copyOf(sent);
    }

    /** Forget the commands sent so far; the stubbed answers stay. */
    public void clear() {
        sent.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                         long timeout, TimeUnit unit) {
        sent.add(command);
        var answer = answers.get(command.getClass());
        if (answer == null) return CompletableFuture.completedFuture(null);
        try {
            return CompletableFuture.completedFuture((R) answer.apply(command));
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    @Override
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                         long timeout, TimeUnit unit, String idempotencyKey) {
        return send(command, metadata, handledMessage, timeout, unit);
    }

    @Override
    public boolean supportsIdempotencyKeys() {
        return true;
    }
}
