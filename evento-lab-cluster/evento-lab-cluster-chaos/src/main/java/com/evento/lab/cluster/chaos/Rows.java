package com.evento.lab.cluster.chaos;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plain rows read from the databases. The invariants compute over these only. */
public final class Rows {

    private Rows() {
    }

    /** One row of {@code es__events} (the message text is kept only for idempotency-key scans). */
    public record EsEvent(long seq, String aggregateId, String eventName, String context, String message) {
        public boolean is(String simpleName) {
            return eventName != null && eventName.endsWith(simpleName);
        }
    }

    /** One row of {@code order_view} (a cell database) or {@code order_view_global}. */
    public record ViewRow(String orderId, String context, String status, int itemCount, int itemQty,
                          String paymentStatus, long lastSeq, int appliedEvents, int staleSkips) {
    }

    /** One row of {@code observed_events}: deliveries of one event to one observer lane. */
    public record ObservedRow(String lane, long seq, String eventName, String orderId, int deliveries) {
    }

    /** Payment attempts of one order, summed over the cell databases. */
    public record PaymentRow(String orderId, int attempts) {
    }

    /** One row of {@code catalog_price} in one cell database. */
    public record PriceRow(String cell, String sku, long priceCents, String context, long lastSeq,
                           int appliedEvents, int staleSkips, String updatedBy) {
    }

    /**
     * One consumer checkpoint.
     *
     * @param db the database holding it: a cell ({@code EU}, ...) or {@code consumers}
     */
    public record ConsumerStateRow(String db, String consumerId, String kind, long lastSequence, String updatedAt) {

        /** {@code <db>/<consumerId>}: the same consumer id in two databases is two independent lanes. */
        public String key() {
            return db + "/" + consumerId;
        }
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

    /**
     * Everything the invariants need, read in one go; a section that failed to load is null.
     *
     * @param cellViews cell -> the {@code order_view} rows of that cell's database
     * @param global    {@code order_view_global}
     */
    public record Snapshot(
            String prefix,
            List<EsEvent> events,
            Long esHead,
            List<Long> duplicateSeqsGlobal,
            Map<String, List<ViewRow>> cellViews,
            List<ViewRow> global,
            List<ObservedRow> observed,
            List<PaymentRow> payments,
            List<ConsumerStateRow> consumers,
            Catalog catalog,
            List<String> loadErrors) {
    }
}
