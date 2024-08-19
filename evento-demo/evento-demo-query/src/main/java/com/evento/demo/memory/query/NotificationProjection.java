package com.evento.demo.memory.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.demo.api.query.NotificationFindAllQuery;
import com.evento.demo.api.query.NotificationFindByIdQuery;
import com.evento.demo.api.utils.Utils;
import com.evento.demo.api.view.NotificationView;
import com.evento.demo.memory.query.external.ExternalNotificationService;

@Projection
public class NotificationProjection {

	private final ExternalNotificationService service;

	public NotificationProjection(ExternalNotificationService service) {
		this.service = service;
	}

	@QueryHandler
	Single<NotificationView> query(NotificationFindByIdQuery query) {
		Utils.logMethodFlow(this, "query", query, "BEGIN");
		var result = service.findById(query.getNotificationId());
		Utils.logMethodFlow(this, "query", query, "END");
		return Single.of(result);
	}

	@QueryHandler
	Multiple<NotificationView> query(NotificationFindAllQuery query) {
		Utils.logMethodFlow(this, "query", query, "BEGIN");
		var result = service.findAll();
		Utils.logMethodFlow(this, "query", query, "END");
		return Multiple.of(result);
	}
}
