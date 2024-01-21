package org.evento.demo.api.query;

import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.Single;
import org.evento.demo.api.view.NotificationView;

public class NotificationFindByIdQuery extends Query<Single<NotificationView>> {

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
