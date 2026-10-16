package com.evento.lab.ryw;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;

@Projection
public class RywProjection {

    @QueryHandler
    Single<NoteView> handle(FindNoteQuery query) {
        var text = RywStore.notes.get(query.getNoteId());
        return Single.of(text == null ? null : new NoteView(query.getNoteId(), text));
    }
}
