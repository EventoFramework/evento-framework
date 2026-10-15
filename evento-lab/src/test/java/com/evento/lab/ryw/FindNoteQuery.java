package com.evento.lab.ryw;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;

public class FindNoteQuery extends Query<Single<NoteView>> {
    private String noteId;

    public FindNoteQuery() {}

    public FindNoteQuery(String noteId) {
        this.noteId = noteId;
    }

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }
}
