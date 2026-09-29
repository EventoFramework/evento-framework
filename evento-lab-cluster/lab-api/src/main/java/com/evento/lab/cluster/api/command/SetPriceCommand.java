package com.evento.lab.cluster.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

/**
 * Sets the world-wide list price of one SKU. The resulting event carries the
 * literal context {@link com.evento.lab.cluster.api.Contexts#GLOBAL}: no
 * regional consumer sees it, the single global lane fans it out to every
 * regional database.
 *
 * <p>A service command: its lock id serialises price changes per SKU and
 * doubles as the {@code aggregate_id} the server stores the event under
 * ({@code price-<sku>}).
 */
public class SetPriceCommand extends ServiceCommand {

    public static final String LOCK_PREFIX = "price-";

    private String sku;
    private long priceCents;
    private String currency;
    private String origin;
    private String idempotencyKey;

    public SetPriceCommand() {
    }

    public SetPriceCommand(String sku, long priceCents, String currency, String origin, String idempotencyKey) {
        this.sku = sku;
        this.priceCents = priceCents;
        this.currency = currency;
        this.origin = origin;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public String getLockId() {
        return LOCK_PREFIX + sku;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    /** The region whose gateway accepted the change (informational). */
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
