package com.evento.lab.ryw;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class NoteAddedEvent extends ServiceEvent {
    private String noteId;
    private String text;

    public NoteAddedEvent() {}

    public NoteAddedEvent(String noteId, String text) {
        this.noteId = noteId;
        this.text = text;
    }

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
