package com.evento.application.client.stream;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.gateway.QueryStream;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.modeling.messaging.query.QueryStreamBatch;
import com.evento.transport.message.ResponseError;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

/**
 * The views of a streamed query, decoded frame by frame from an {@link InboundStream}.
 *
 * <p>A frame is the encoded reply of the answering bundle: a batch of views, the whole
 * answer (when the answering bundle did not stream), or the exception the query threw —
 * rethrown here as the cause of a {@link QueryStream.QueryStreamException}, the same
 * exception a plain query would have failed with.
 *
 * @param <V> the view type
 */
public final class FrameQueryStream<V extends View> implements QueryStream<V> {

    private final InboundStream frames;
    private final AdminPayloadCodec codec;
    private final BiFunction<ResponseError, String, RuntimeException> errors;
    private final Iterator<V> views = new Views();
    private Iterator<? extends View> current = Collections.emptyIterator();
    private boolean ended;

    /**
     * @param errors turns a transport-level failure into the exception the caller sees
     */
    public FrameQueryStream(InboundStream frames, AdminPayloadCodec codec,
                            BiFunction<ResponseError, String, RuntimeException> errors) {
        this.frames = frames;
        this.codec = codec;
        this.errors = errors;
    }

    @Override
    public Iterator<V> iterator() {
        return views;
    }

    @Override
    public void close() {
        frames.close();
    }

    private final class Views implements Iterator<V> {

        @Override
        public boolean hasNext() {
            while (!current.hasNext()) {
                if (ended) return false;
                pull();
            }
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V next() {
            if (!hasNext()) throw new NoSuchElementException();
            return (V) current.next();
        }
    }

    private void pull() {
        InboundStream.Item item;
        try {
            item = frames.next();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            frames.close();
            ended = true;
            throw new QueryStreamException("interrupted while waiting for the next views", e);
        } catch (InboundStream.StreamFailedException failed) {
            ended = true;
            var cause = errors.apply(failed.error(), frames.payloadType());
            throw new QueryStreamException(cause.getMessage(), cause);
        }
        switch (item) {
            case InboundStream.Item.End ignored -> ended = true;
            case InboundStream.Item.Frame(byte[] payload) -> current = decode(payload);
            case InboundStream.Item.Whole(byte[] payload) -> current = decode(payload);
        }
    }

    private Iterator<? extends View> decode(byte[] payload) {
        Object body;
        try {
            body = codec.decodeResponse(payload).getBody();
        } catch (RuntimeException corrupt) {
            frames.close();
            ended = true;
            throw new QueryStreamException("undecodable frame of " + frames.payloadType(), corrupt);
        }
        return switch (body) {
            case QueryStreamBatch batch -> batch.getItems().iterator();
            case QueryResponse<?> whole -> QueryStreamBatch.views(whole).iterator();
            case ExceptionWrapper wrapped -> {
                frames.close();
                ended = true;
                var cause = wrapped.toException();
                throw new QueryStreamException(cause.getMessage(), cause);
            }
            case null -> Collections.emptyIterator();
            default -> {
                frames.close();
                ended = true;
                throw new QueryStreamException("unexpected frame body " + body.getClass().getName()
                        + " in the stream of " + frames.payloadType());
            }
        };
    }
}
