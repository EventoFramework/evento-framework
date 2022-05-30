package org.eventrails.demo.api.event;

import org.eventrails.modeling.messaging.payload.ServiceEvent;

public class NotificationSentEvent extends ServiceEvent {
	private String notificationId;
	private String body;

	public NotificationSentEvent(String notificationId, String body) {
		this.notificationId = notificationId;
		this.body = body;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(String notificationId) {
		this.notificationId = notificationId;
	}
}
