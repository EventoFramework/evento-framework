package org.eventrails.demo.query;

import org.eventrails.common.utils.Inject;
import org.eventrails.demo.api.query.NotificationFindAllQuery;
import org.eventrails.demo.api.query.NotificationFindByIdQuery;
import org.eventrails.demo.api.utils.Utils;
import org.eventrails.demo.api.view.NotificationView;
import org.eventrails.common.modeling.annotations.component.Projection;
import org.eventrails.common.modeling.annotations.handler.QueryHandler;
import org.eventrails.common.modeling.messaging.query.Multiple;
import org.eventrails.common.modeling.messaging.query.Single;
import org.eventrails.demo.query.external.ExternalNotificationService;

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
