package com.evento.spring.boot.app;

import java.util.Map;

/** An ordinary Spring bean the projection gets through its constructor. */
public class PriceBook {

    private final Map<String, Integer> prices = Map.of("pen", 3, "ink", 7);

    public int priceOf(String item) {
        return prices.getOrDefault(item, 0);
    }
}
