package com.evento.demo.memory.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.demo.api.event.AggregateStressCalledEvent;
import com.evento.demo.api.event.ServiceStressCalledEvent;
import com.evento.demo.api.utils.StressDB;

import java.time.ZonedDateTime;

@Projector(version = 1)
public class StressProjector {

    private final StressDB stressDB;

    public StressProjector(StressDB stressDB) {
        this.stressDB = stressDB;
    }

    @EventHandler
    public void on(AggregateStressCalledEvent event){
        stressDB.stressInstanceProcessed(
                event.getStressIdentifier(),
                event.getInstance(),
                ZonedDateTime.now()
        );
    }

    @EventHandler
    public void on(ServiceStressCalledEvent event){
        stressDB.stressInstanceProcessed(
                event.getStressIdentifier(),
                event.getInstance(),
                ZonedDateTime.now()
        );
    }
}
