package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.LabUtilEvent;

import java.util.HashMap;

/**
 * Demonstrates configurable retry-on-failure: fails event.failEvent times,
 * then succeeds. The caller controls the failure count via the command payload.
 */
@Projector(version = 1)
public class LabUtilProjector {

    private final HashMap<Long, Long> hitCounts = new HashMap<>();

    @EventHandler(retry = 1)
    void on(LabUtilEvent event, Long eventSequenceNumber) {
        var hits = hitCounts.getOrDefault(eventSequenceNumber, 0L);
        hitCounts.put(eventSequenceNumber, hits + 1);
        if (hits < event.getFailEvent()) {
            System.out.println(getClass() + " FAIL attempt " + (hits + 1) + " for seq=" + eventSequenceNumber);
            throw new RuntimeException("LabUtilProjector: deliberate failure");
        }
        System.out.println(getClass() + " OK at attempt " + (hits + 1) + " for seq=" + eventSequenceNumber);
    }
}
