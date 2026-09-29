package com.evento.application.client;

import com.evento.transport.SendFailedException;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which replies trigger the reactive fail-back (N16): only the broker's routing
 * miss, never a handler's own failure or a local refusal.
 */
class NoHandlerDetectionTest {

    private static Response failure(Throwable t) {
        return Response.failure(UUID.randomUUID(), ResponseError.of(t));
    }

    @Test
    void theBrokersRoutingMissesCount() {
        assertThat(BundleClient.isNoHandler(failure(new IllegalStateException("no handler for Cmd")))).isTrue();
        assertThat(BundleClient.isNoHandler(failure(new IllegalStateException("no available handler for Cmd"))))
                .isTrue();
    }

    @Test
    void otherFailuresAndSuccessesDoNot() {
        assertThat(BundleClient.isNoHandler(Response.success(UUID.randomUUID(), "Cmd", new byte[0]))).isFalse();
        assertThat(BundleClient.isNoHandler(failure(new IllegalStateException("handler vanished: x")))).isFalse();
        // The bundle's own dispatcher when a handler is missing locally: not a routing miss.
        assertThat(BundleClient.isNoHandler(failure(new IllegalStateException("no handler registered for Cmd"))))
                .isFalse();
        assertThat(BundleClient.isNoHandler(failure(new RuntimeException("no handler for Cmd")))).isFalse();
        assertThat(BundleClient.isNoHandler(failure(new SendFailedException("not sent", null)))).isFalse();
        assertThat(BundleClient.isNoHandler(null)).isFalse();
    }
}
