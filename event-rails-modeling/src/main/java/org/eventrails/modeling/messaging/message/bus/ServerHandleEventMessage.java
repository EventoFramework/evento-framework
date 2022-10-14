package org.eventrails.modeling.messaging.message.bus;

import org.eventrails.modeling.gateway.PublishedEvent;

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
