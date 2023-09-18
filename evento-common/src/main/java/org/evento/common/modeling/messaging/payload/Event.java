package org.evento.common.modeling.messaging.payload;

import org.evento.common.utils.Context;

public abstract class Event extends Payload {
    private String context = Context.DEFAULT;

    public String getContext() {
        return context;
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> T setContext(String context) {
        if(context ==  null){
            throw new IllegalArgumentException();
        }
        this.context = context;
        return (T) this;
    }
}
