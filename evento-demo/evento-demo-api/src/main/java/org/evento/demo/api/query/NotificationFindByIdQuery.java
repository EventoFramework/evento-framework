package org.evento.demo.api.query;

import org.evento.common.modeling.messaging.payload.Query;

public class NotificationFindByIdQuery extends Query {

	private String notificationId;

	public NotificationFindByIdQuery(String notificationId) {
		this.notificationId = notificationId;
	}

	public NotificationFindByIdQuery() {
	}

	public String getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(String notificationId) {
		this.notificationId = notificationId;
	}
}
