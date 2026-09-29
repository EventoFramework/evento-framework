package com.evento.lab.cluster.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

/**
 * A world-wide list price changed. Always published with the literal context
 * {@link com.evento.lab.cluster.api.Contexts#GLOBAL} ({@code "ALL"}), never
 * with a region: it is the lab's example of a cross-region event.
 */
public class PriceChangedEvent extends ServiceEvent {

    private String sku;
    private long priceCents;
    private String currency;
    private String origin;
    private String idempotencyKey;

    public PriceChangedEvent() {
    }

    public PriceChangedEvent(String sku, long priceCents, String currency, String origin, String idempotencyKey) {
        this.sku = sku;
        this.priceCents = priceCents;
        this.currency = currency;
        this.origin = origin;
        this.idempotencyKey = idempotencyKey;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
