package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.View;

public class PriceView implements View {
    int price;
    public PriceView() {}
    public PriceView(int price) { this.price = price; }
}
