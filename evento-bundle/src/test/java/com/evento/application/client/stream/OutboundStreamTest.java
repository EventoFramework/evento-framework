package com.evento.application.client.stream;

import com.evento.application.client.handler.HandlerRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cancelling a stream is idempotent: the handler sees the cancellation, never a semaphore overflow. */
class OutboundStreamTest {

    private static OutboundStream stream() {
        return new OutboundStream(UUID.randomUUID(), m -> CompletableFuture.completedFuture(null), 4,
                Duration.ofSeconds(5), false, 0);
    }

    @Test
    void aStreamCancelledTwiceStillTellsTheHandlerItWasCancelled() {
        var s = stream();
        s.cancel("caller closed");
        assertThatCode(() -> s.cancel("no credit")).doesNotThrowAnyException();
        assertThatCode(() -> s.grant(8)).doesNotThrowAnyException();
        assertThatThrownBy(() -> s.send(new byte[]{1}))
                .isInstanceOf(HandlerRegistry.StreamCancelledException.class)
                .hasMessageContaining("caller closed");
        assertThat(s.isCancelled()).isTrue();
    }

    @Test
    void aSendWaitingForCreditWakesUpOnCancel() throws Exception {
        var s = new OutboundStream(UUID.randomUUID(), m -> CompletableFuture.completedFuture(null), 1,
                Duration.ofSeconds(30), false, 0);
        s.send(new byte[]{1});  // uses the only credit
        var waiting = CompletableFuture.runAsync(() -> {
            try {
                s.send(new byte[]{2});
            } catch (HandlerRegistry.StreamCancelledException e) {
                throw new IllegalStateException("cancelled", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(200);
        s.cancel("caller closed");
        assertThatThrownBy(waiting::join).hasRootCauseInstanceOf(HandlerRegistry.StreamCancelledException.class);
    }
}
