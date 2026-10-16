package com.evento.lab.ryw;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;

@Service
public class RywService {

    @CommandHandler
    public NoteAddedEvent handle(AddNoteCommand command) {
        return new NoteAddedEvent(command.getNoteId(), command.getText());
    }
}
