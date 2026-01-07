package com.evento.common.modeling.messaging.message.internal;

import java.util.concurrent.TimeoutException;

public interface Expirable {
    public boolean checkExpired();

    default void throwExpired() throws TimeoutException {
        if(checkExpired()){
            throw new TimeoutException();
        }
    }
}
