package org.eventrails.demo.api.query;

import org.eventrails.demo.api.view.NotificationView;
import org.eventrails.modeling.messaging.payload.Query;
import org.eventrails.modeling.messaging.payload.View;
import org.eventrails.modeling.messaging.query.Single;

public class NotificationFindByIdQuery extends Query<Single<NotificationView>> {

	public String getNotificationId() {
		return notificationId;
	}

	public void setNotificationId(String notificationId) {
		this.notificationId = notificationId;
	}

	public NotificationFindByIdQuery() {
	}

	private String notificationId;
}
