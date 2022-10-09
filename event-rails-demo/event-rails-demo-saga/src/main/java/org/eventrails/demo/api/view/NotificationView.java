package org.eventrails.demo.api.view;

import org.eventrails.modeling.messaging.payload.View;

public class NotificationView extends View {

	private String notificationId;
	private String body;

	public NotificationView(String notificationId, String body) {
		this.notificationId = notificationId;
		this.body = body;
	}

	public NotificationView() {
	}

	public String getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(String notificationId) {
		this.notificationId = notificationId;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
