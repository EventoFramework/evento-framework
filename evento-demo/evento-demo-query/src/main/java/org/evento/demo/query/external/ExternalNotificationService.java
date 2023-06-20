package org.evento.demo.query.external;

import org.evento.demo.api.view.NotificationView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExternalNotificationService {
	public NotificationView findById(String notificationId) {
		return new NotificationView(notificationId, notificationId);
	}

	public List<NotificationView> findAll() {
		return List.of(findById("notification1"), findById("notification2"), findById("notification3"));
	}
}
