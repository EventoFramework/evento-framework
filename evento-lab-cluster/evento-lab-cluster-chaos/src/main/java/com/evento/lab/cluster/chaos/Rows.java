package com.evento.lab.cluster.chaos;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plain rows read from the two databases. The invariants compute over these only. */
public final class Rows {

    private Rows() {
    }

    /** One row of {@code es__events} (the message text is kept only for idempotency-key scans). */
    public record EsEvent(long seq, String aggregateId, String eventName, String context, String message) {
        public boolean is(String simpleName) {
            return eventName != null && eventName.endsWith(simpleName);
        }
    }

    /** One row of an {@code order_view_*} table. */
    public record ViewRow(String orderId, String context, String status, int itemCount, int itemQty,
                          String paymentStatus, long lastSeq, int appliedEvents, int staleSkips) {
    }

    public record ObservedRow(long seq, String eventName, String orderId, int deliveries) {
    }

    public record PaymentRow(String orderId, int attempts) {
    }

    public record ConsumerStateRow(String consumerId, String kind, long lastSequence, String updatedAt) {
    }

    /**
     * The server catalog.
     *
     * @param bundles           {@code core__bundle.id -> instance_id}
     * @param handlersByPayload handled payload -> bundle ids that declare a handler for it
     * @param consumers         {@code core__consumer.consumer_id -> instance_id}
     */
    public record Catalog(Map<String, String> bundles, Map<String, Set<String>> handlersByPayload,
                          Map<String, String> consumers) {
    }

    /** Everything the invariants need, read in one go; a section that failed to load is null. */
    public record Snapshot(
            String prefix,
            List<EsEvent> events,
            Long esHead,
            List<Long> duplicateSeqsGlobal,
            Map<String, List<ViewRow>> regional,
            List<ViewRow> global,
            List<ObservedRow> observed,
            List<PaymentRow> payments,
            List<ConsumerStateRow> consumers,
            Catalog catalog,
            List<String> loadErrors) {
    }
}
