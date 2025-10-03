package com.evento.demo.memory.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.demo.api.event.DemoUpdatedEvent;
import com.evento.demo.api.event.UtilEvent;
import com.evento.demo.api.utils.Utils;

import java.util.HashMap;

@Projector(version = 1)
public class UtilProjector {

    private final HashMap<Long, Long> memoryService = new HashMap<>();


    @EventHandler(retry = 1)
    void on(UtilEvent event,
            Long eventSequenceNumber) {
        var hits = memoryService.getOrDefault(eventSequenceNumber, 0L);
        memoryService.put(eventSequenceNumber, hits + 1);
        if(hits < event.getFailEvent()){
            Utils.logMethodFlow(this, "on", event, "FAIL FOR TEST");
            throw new RuntimeException("FAIL FOR TEST");
        }

        Utils.logMethodFlow(this, "on", event, "OK DURING REPROCESS");

    }
}
