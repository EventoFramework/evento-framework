package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class NoteAdded extends DomainEvent {
    String orderId;
    String note;
    public NoteAdded() {}
    public NoteAdded(String orderId, String note) { this.orderId = orderId; this.note = note; }
    public String orderId() { return orderId; }
    public String note() { return note; }
}
