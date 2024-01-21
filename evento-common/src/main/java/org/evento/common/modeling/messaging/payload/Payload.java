package org.evento.common.modeling.messaging.payload;

import java.io.Serializable;

/**
 * The Payload class is an abstract class that represents a payload object.
 * It is Serializable, which means it can be converted into a byte stream and sent over a network or stored in a file.
 * This class does not provide any implementation and is meant to be extended by concrete payload classes.
 */
public abstract class Payload implements Serializable {

}
