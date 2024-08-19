package com.evento.server.service.performance.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AggregatePerformancePoint {
    private String timestamp;
    private BigDecimal serviceTime;
    private BigDecimal count;
    private BigDecimal store;
    private BigDecimal retrieve;
    private BigDecimal lock;
}
