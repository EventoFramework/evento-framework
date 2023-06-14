package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Invocation;

public class InvocationMessage<T extends Invocation> extends Message<T>{

    private final String name;

    public InvocationMessage(String name) {
      this.name = name;
    }

    @Override
    public String getPayloadName() {
        return name;
    }
}
