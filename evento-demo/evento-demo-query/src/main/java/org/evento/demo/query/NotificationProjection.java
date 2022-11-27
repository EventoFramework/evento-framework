package org.evento.demo.query;

import org.evento.common.utils.Inject;
import org.evento.demo.api.query.NotificationFindAllQuery;
import org.evento.demo.api.query.NotificationFindByIdQuery;
import org.evento.demo.api.utils.Utils;
import org.evento.demo.api.view.NotificationView;
import org.evento.common.modeling.annotations.component.Projection;
import org.evento.common.modeling.annotations.handler.QueryHandler;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.modeling.messaging.query.Single;
import org.evento.demo.query.external.ExternalNotificationService;

@Projection
public class NotificationProjection {

	@Inject
	private ExternalNotificationService service;

	@QueryHandler
	Single<NotificationView> query(NotificationFindByIdQuery query){
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = service.findById(query.getNotificationId());
		Utils.logMethodFlow(this,"query", query, "END");
		return Single.of(result);
	}

	@QueryHandler
	Multiple<NotificationView> query(NotificationFindAllQuery query){
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = service.findAll();
		Utils.logMethodFlow(this,"query", query, "END");
		return Multiple.of(result);
	}
}
