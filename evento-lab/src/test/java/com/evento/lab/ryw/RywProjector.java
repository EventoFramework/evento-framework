package com.evento.lab.ryw;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;

import java.util.concurrent.TimeUnit;

@Projector(version = 1)
public class RywProjector {

    @EventHandler
    void on(NoteAddedEvent event) throws InterruptedException {
        // A lagging read model: the test decides when the write becomes visible.
        RywStore.gate.await(60, TimeUnit.SECONDS);
        RywStore.notes.put(event.getNoteId(), event.getText());
    }
}
