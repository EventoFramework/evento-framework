package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderUpdatedEvent;

import java.util.HashMap;

/**
 * Demonstrates retry-on-failure: fails the first 3 attempts for each event sequence,
 * then succeeds. Shows @EventHandler(retry=3) behaviour.
 */
@Projector(version = 1)
public class LabFailProjector {

    private final HashMap<Long, Long> hitCounts = new HashMap<>();

    @EventHandler(retry = 3)
    void on(OrderUpdatedEvent event, Long eventSequenceNumber) {
        var hits = hitCounts.getOrDefault(eventSequenceNumber, 0L);
        hitCounts.put(eventSequenceNumber, hits + 1);
        if (hits <= 2) {
            System.out.println(getClass() + " FAIL attempt " + (hits + 1) + " for seq=" + eventSequenceNumber);
            throw new RuntimeException("LabFailProjector: deliberate failure for retry test");
        }
        System.out.println(getClass() + " OK after retry for seq=" + eventSequenceNumber);
    }
}
