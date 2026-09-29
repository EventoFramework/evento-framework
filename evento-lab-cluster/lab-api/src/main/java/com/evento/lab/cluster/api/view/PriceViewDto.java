package com.evento.lab.cluster.api.view;

import com.evento.common.modeling.messaging.payload.View;

/** One row of {@code catalog_price} in one regional database. */
public class PriceViewDto implements View {

    private String sku;
    private long priceCents;
    private String currency;
    private String context;
    private long lastSeq;
    private int appliedEvents;
    private int staleSkips;
    private String updatedBy;
    private String database;

    public PriceViewDto() {
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public long getLastSeq() { return lastSeq; }
    public void setLastSeq(long lastSeq) { this.lastSeq = lastSeq; }
    public int getAppliedEvents() { return appliedEvents; }
    public void setAppliedEvents(int appliedEvents) { this.appliedEvents = appliedEvents; }
    public int getStaleSkips() { return staleSkips; }
    public void setStaleSkips(int staleSkips) { this.staleSkips = staleSkips; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    /** Which regional database answered (the fan-out target name, e.g. {@code eu}). */
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
}
