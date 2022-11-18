package org.eventrails.bus;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.serialization.ObjectMapperUtils;

import java.io.IOException;
import java.io.Serializable;

public class EventRailsMessage implements Serializable {
    private NodeAddress dest;
    private NodeAddress source;
    private Serializable message;

    private EventRailsMessageType type;

    public EventRailsMessage() {
    }

    public static String create(NodeAddress source, NodeAddress dest, Serializable message)  {
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                    new EventRailsMessage(source, dest, EventRailsMessageType.DATA, message)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String joinMessage(EventRailsNodeAddress address) {
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                    new EventRailsMessage(address, null, EventRailsMessageType.CONNECT, null)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String viewUpdateMessage(NodeAddress dest, ViewUpdate viewUpdate){
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                    new EventRailsMessage(null, dest, EventRailsMessageType.VIEW, viewUpdate)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String killMessage(NodeAddress source, NodeAddress dest) throws JsonProcessingException {
        return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
                new EventRailsMessage(source, dest, EventRailsMessageType.KILL, null)
        );
    }

    public static EventRailsMessage parse(String body) throws IOException {
        return ObjectMapperUtils.getPayloadObjectMapper().readValue(body, EventRailsMessage.class);
    }

    public EventRailsMessage(NodeAddress source, NodeAddress dest, EventRailsMessageType type,  Serializable message) {
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

    public EventRailsMessageType getType() {
        return type;
    }

    public void setType(EventRailsMessageType type) {
        this.type = type;
    }

    public NodeAddress getSource() {
        return source;
    }

    public void setSource(NodeAddress source) {
        this.source = source;
    }
}
