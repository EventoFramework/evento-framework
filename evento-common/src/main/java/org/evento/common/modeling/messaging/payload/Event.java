package org.evento.common.modeling.messaging.payload;

import org.evento.common.utils.Context;

public abstract class Event extends Payload {
    private String context = Context.DEFAULT;

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        if(context ==  null){
            throw new IllegalArgumentException();
        }
        this.context = context;
    }
}
