package org.evento.bus.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.evento.common.serialization.ObjectMapperUtils;

import java.io.IOException;
import java.io.Serializable;

public class RabbitMqMessage implements Serializable {
	private String sourceNodeId;
	private String sourceBundleId;
	private long sourceBundleVersion;
	private Serializable sourceNodeAddress;
	private Serializable message;

	public RabbitMqMessage(RabbitMqNodeAddress source, Serializable message) {
		this.sourceNodeAddress = source.getAddress();
		this.sourceBundleId = source.getBundleId();
		this.sourceBundleVersion = source.getBundleVersion();
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
		return new RabbitMqNodeAddress(sourceBundleId, sourceBundleVersion, sourceNodeAddress, sourceNodeId);
	}

	public String getSourceNodeId() {
		return sourceNodeId;
	}

	public void setSourceNodeId(String sourceNodeId) {
		this.sourceNodeId = sourceNodeId;
	}

	public String getSourceBundleId() {
		return sourceBundleId;
	}

	public void setSourceBundleId(String sourceBundleId) {
		this.sourceBundleId = sourceBundleId;
	}

	public Serializable getSourceNodeAddress() {
		return sourceNodeAddress;
	}

	public void setSourceNodeAddress(Serializable sourceNodeAddress) {
		this.sourceNodeAddress = sourceNodeAddress;
	}

	public long getSourceBundleVersion() {
		return sourceBundleVersion;
	}

	public void setSourceBundleVersion(long sourceBundleVersion) {
		this.sourceBundleVersion = sourceBundleVersion;
	}

	public Serializable getMessage() {
		return message;
	}

	public void setMessage(Serializable message) {
		this.message = message;
	}
}
