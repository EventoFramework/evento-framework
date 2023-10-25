package org.evento.application.bus;

import java.io.Serializable;

public interface RequestHandler {

    Serializable handle(Serializable serializable) throws Exception;
}
