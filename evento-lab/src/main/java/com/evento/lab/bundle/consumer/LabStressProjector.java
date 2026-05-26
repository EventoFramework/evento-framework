package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.StressAggregateCalledEvent;
import com.evento.lab.api.event.StressServiceCalledEvent;
import com.evento.lab.bundle.LabStore;

@Projector(version = 1)
public class LabStressProjector {

    @EventHandler
    void on(StressAggregateCalledEvent event) {
        LabStore.incrementStress(event.getStressId() + "_" + event.getInstance());
    }

    @EventHandler
    void on(StressServiceCalledEvent event) {
        LabStore.incrementStress(event.getStressId() + "_" + event.getInstance());
    }
}
