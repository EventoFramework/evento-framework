package org.evento.application.bus;

import java.io.Serializable;

/**
 * The RequestHandler interface represents a contract for handling requests.
 * Implementations of this interface should provide implementation for the handle method.
 */
public interface RequestHandler {

    /**
     * This method handles a serializable object.
     *
     * @param serializable the serializable object to be handled
     * @return the handled serializable object
     * @throws Exception if an error occurs during handling
     */
    Serializable handle(Serializable serializable) throws Exception;
}
