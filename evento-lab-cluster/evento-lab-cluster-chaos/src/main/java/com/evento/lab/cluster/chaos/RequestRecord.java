package com.evento.lab.cluster.chaos;

/**
 * One HTTP call through the location-aware LB.
 *
 * @param startMs         wall clock at send
 * @param latencyMs       client-observed latency
 * @param region          the X-Region routing key sent
 * @param op              create | add_item | complete | get_regional | get_global | whoami
 * @param orderId         order the call was about
 * @param status          HTTP status, or 0 when no response arrived
 * @param outcome         see {@link Outcome}
 * @param kind            the gateway's failure classifier ({@code no_handler}, {@code timeout}, ...)
 * @param error           error text, truncated
 * @param servedBy        the gateway that answered ({@code X-Lab-Upstream}), may be null
 * @param idempotencyKey  the X-Idempotency-Key sent, may be null
 */
public record RequestRecord(
        long startMs,
        long latencyMs,
        String region,
        String op,
        String orderId,
        int status,
        Outcome outcome,
        String kind,
        String error,
        String servedBy,
        String idempotencyKey) {

    public enum Outcome {
        /** 2xx: Evento acknowledged, the event is stored. */
        ACK,
        /** 409: the domain refused it. Nothing stored. */
        REJECTED,
        /** 404 on a read. */
        NOT_FOUND,
        /** 5xx from the gateway: the outcome of the command is unknown. */
        ERROR,
        /** no response in time, or connection failure: unknown outcome. */
        NO_RESPONSE;

        /** Whether the command may have been stored even though it was not acknowledged. */
        public boolean indeterminate() {
            return this == ERROR || this == NO_RESPONSE;
        }
    }

    public boolean isCommand() {
        return op.equals("create") || op.equals("add_item") || op.equals("complete");
    }
}
