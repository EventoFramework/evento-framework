package org.evento.common.modeling.messaging.message.internal;

import org.evento.common.modeling.messaging.dto.PublishedEvent;

import java.io.Serializable;

public class ServerHandleEventMessage implements Serializable {

	private PublishedEvent publishedEvent;

	public ServerHandleEventMessage() {
	}

	public ServerHandleEventMessage(PublishedEvent publishedEvent) {
		this.publishedEvent = publishedEvent;
	}

	public PublishedEvent getPublishedEvent() {
		return publishedEvent;
	}

	public void setPublishedEvent(PublishedEvent publishedEvent) {
		this.publishedEvent = publishedEvent;
	}
}
