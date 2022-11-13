package org.eventrails.bus.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.common.serialization.ObjectMapperUtils;

import java.io.IOException;
import java.io.Serializable;

public class RabbitMqMessage implements Serializable {
	private String sourceNodeId;
	private String sourceNodeName;
	private Serializable sourceNodeAddress;
	private Serializable message;

	public RabbitMqMessage(RabbitMqNodeAddress source, Serializable message) {
		this.sourceNodeAddress = source.getAddress();
		this.sourceNodeName = source.getNodeName();
		this.sourceNodeId = source.getNodeId();
		this.message = message;
	}

	public RabbitMqMessage() {
	}

	public static byte[] create(RabbitMqNodeAddress address, Serializable message) throws JsonProcessingException {
		return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(
				new RabbitMqMessage(address, message)
		).getBytes();
	}

	public static RabbitMqMessage parse(byte[] body) throws IOException {
		return ObjectMapperUtils.getPayloadObjectMapper().readValue(body, RabbitMqMessage.class);
	}

	public RabbitMqNodeAddress getSource(){
		return new RabbitMqNodeAddress(sourceNodeName, sourceNodeAddress, sourceNodeId);
	}

	public String getSourceNodeId() {
		return sourceNodeId;
	}

	public void setSourceNodeId(String sourceNodeId) {
		this.sourceNodeId = sourceNodeId;
	}

	public String getSourceNodeName() {
		return sourceNodeName;
	}

	public void setSourceNodeName(String sourceNodeName) {
		this.sourceNodeName = sourceNodeName;
	}

	public Serializable getSourceNodeAddress() {
		return sourceNodeAddress;
	}

	public void setSourceNodeAddress(Serializable sourceNodeAddress) {
		this.sourceNodeAddress = sourceNodeAddress;
	}

	public Serializable getMessage() {
		return message;
	}

	public void setMessage(Serializable message) {
		this.message = message;
	}
}
