package com.evento.spring.boot.app;

import com.evento.common.modeling.messaging.payload.View;

public class PriceView implements View {
    private String item;
    private int price;

    public PriceView() {
    }

    public PriceView(String item, int price) {
        this.item = item;
        this.price = price;
    }

    public String getItem() { return item; }
    public int getPrice() { return price; }
}
