package com.evento.lab.cluster.chaos;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the client knows about one order: how many of each command were
 * acknowledged, and how many were attempted with an unknown outcome. This is
 * the reference the event store is checked against for I1 (nothing
 * acknowledged is lost) and I2 (nothing is stored more often than it could
 * have been sent).
 */
public final class OrderLedger {

    public final String orderId;
    public final String region;
    public int createAcked;
    public int createIndeterminate;
    public int itemsAcked;
    public int itemsIndeterminate;
    public int completeAcked;
    public int completeIndeterminate;

    public OrderLedger(String orderId, String region) {
        this.orderId = orderId;
        this.region = region;
    }

    public synchronized void record(RequestRecord r) {
        var ack = r.outcome() == RequestRecord.Outcome.ACK;
        var unknown = r.outcome().indeterminate();
        switch (r.op()) {
            case "create" -> {
                if (ack) createAcked++;
                if (unknown) createIndeterminate++;
            }
            case "add_item" -> {
                if (ack) itemsAcked++;
                if (unknown) itemsIndeterminate++;
            }
            case "complete" -> {
                if (ack) completeAcked++;
                if (unknown) completeIndeterminate++;
            }
            default -> {
                // reads do not change the ledger
            }
        }
    }

    public boolean anyAcked() {
        return createAcked + itemsAcked + completeAcked > 0;
    }

    /** Build ledgers from raw records (orders keyed by id, region = first seen). */
    public static Map<String, OrderLedger> fromRecords(Collection<RequestRecord> records) {
        var out = new LinkedHashMap<String, OrderLedger>();
        for (var r : records) {
            if (!r.isCommand() || r.orderId() == null) continue;
            out.computeIfAbsent(r.orderId(), id -> new OrderLedger(id, r.region())).record(r);
        }
        return out;
    }
}
