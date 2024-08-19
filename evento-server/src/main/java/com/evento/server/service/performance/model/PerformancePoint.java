package com.evento.server.service.performance.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PerformancePoint {
    private String timestamp;
    private BigDecimal serviceTime;
    private BigDecimal count;
}
