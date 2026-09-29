package com.evento.lab.cluster.command.bundle;

import com.evento.common.modeling.state.AggregateState;

public class OrderAggregateState extends AggregateState {

    private String region;
    private String status;
    private int itemCount;
    private int itemQty;

    public OrderAggregateState() {
    }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public int getItemQty() { return itemQty; }
    public void setItemQty(int itemQty) { this.itemQty = itemQty; }
}
