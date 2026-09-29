package com.evento.lab.cluster.support.store;

/**
 * One price event as the fan-out applies it.
 *
 * @param seq     the event's sequence number: the idempotency key per target
 * @param sku     the row key
 * @param context the event's context (always {@code ALL} for a well-formed price event)
 */
public record PriceChange(long seq, String sku, long priceCents, String currency, String context) {
}
