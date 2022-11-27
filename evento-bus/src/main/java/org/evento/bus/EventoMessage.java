package org.evento.bus;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.evento.common.modeling.messaging.message.bus.NodeAddress;
import org.evento.common.serialization.ObjectMapperUtils;

import java.io.IOException;
import java.io.Serializable;

public class EventoMessage implements Serializable {
    private NodeAddress dest;
    private NodeAddress source;
    private Serializable message;

    private EventoMessageType type;

    public EventoMessage() {
    }

    public static String create(NodeAddress source, NodeAddress dest, Serializable message)  {
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                    new EventoMessage(source, dest, EventoMessageType.DATA, message)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String joinMessage(EventoNodeAddress address) {
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                    new EventoMessage(address, null, EventoMessageType.CONNECT, null)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String viewUpdateMessage(NodeAddress dest, ViewUpdate viewUpdate){
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                    new EventoMessage(null, dest, EventoMessageType.VIEW, viewUpdate)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String killMessage(NodeAddress source, NodeAddress dest) throws JsonProcessingException {
        return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                new EventoMessage(source, dest, EventoMessageType.KILL, null)
        );
    }

    public static EventoMessage parse(String body) throws IOException {
        return ObjectMapperUtils.getPayloadObjectMapper().readValue(body, EventoMessage.class);
    }

    public EventoMessage(NodeAddress source, NodeAddress dest, EventoMessageType type,  Serializable message) {
        this.source = source;
        this.dest = dest;
        this.message = message;
        this.type = type;
    }




    public NodeAddress getDest() {
        return dest;
    }

    public void setDest(NodeAddress dest) {
        this.dest = dest;
    }

    public Serializable getMessage() {
        return message;
    }

    public void setMessage(Serializable message) {
        this.message = message;
    }

    public EventoMessageType getType() {
        return type;
    }

    public void setType(EventoMessageType type) {
        this.type = type;
    }

    public NodeAddress getSource() {
        return source;
    }

    public void setSource(NodeAddress source) {
        this.source = source;
    }
}
