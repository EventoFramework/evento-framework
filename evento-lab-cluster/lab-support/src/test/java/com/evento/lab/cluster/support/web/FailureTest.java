package com.evento.lab.cluster.support.web;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureTest {

    @Test
    void classifiesTheFailuresTheScenariosCountOn() {
        assertThat(Failure.of(new ExecutionException(new TimeoutException("request timed out after 30000 ms"))))
                .extracting(Failure::status, Failure::kind).containsExactly(500, "timeout");
        assertThat(Failure.of(new CompletionException(new IllegalStateException("no handler for CreateOrderCommand"))))
                .extracting(Failure::status, Failure::kind).containsExactly(500, "no_handler");
        assertThat(Failure.of(new RuntimeException("peer disconnected: evento-apac")).kind()).isEqualTo("disconnected");
        assertThat(Failure.of(new IllegalStateException("order o1 is already completed")))
                .extracting(Failure::status, Failure::kind).containsExactly(409, "rejected");
        assertThat(Failure.of(new RuntimeException("AggregateInitializedError: o1")).kind()).isEqualTo("rejected");
        assertThat(Failure.of(new java.util.NoSuchElementException("order not found: x")).status()).isEqualTo(404);
        assertThat(Failure.of(new RuntimeException("boom")))
                .extracting(Failure::status, Failure::kind).containsExactly(500, "error");
    }

    @Test
    void neverUsesTheStatusesTheLbTreatsAsADeadGateway() {
        for (var t : new Throwable[]{new TimeoutException("timed out"), new RuntimeException("peer disconnected"),
                new RuntimeException("no handler for X"), new RuntimeException("x")}) {
            assertThat(Failure.of(t).status()).isNotIn(502, 503, 504);
        }
    }

    @Test
    void reportsTheInnermostCause() {
        var f = Failure.of(new ExecutionException(new IllegalStateException("no connection for x")));
        assertThat(f.type()).isEqualTo(IllegalStateException.class.getName());
        assertThat(f.message()).isEqualTo("no connection for x");
    }
}
