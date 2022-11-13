package org.eventrails.demo.query;

import org.eventrails.demo.api.query.NotificationFindAllQuery;
import org.eventrails.demo.api.query.NotificationFindByIdQuery;
import org.eventrails.demo.api.view.NotificationView;
import org.eventrails.common.modeling.annotations.component.Projection;
import org.eventrails.common.modeling.annotations.handler.QueryHandler;
import org.eventrails.common.modeling.messaging.query.Multiple;
import org.eventrails.common.modeling.messaging.query.Single;

@Projection
public class NotificationProjection {

	@QueryHandler
	Single<NotificationView> query(NotificationFindByIdQuery query){
		System.out.println(this.getClass() + " - query(NotificationFindByIdQuery)");
		return Single.of(new NotificationView(null, null));
	}

	@QueryHandler
	Multiple<NotificationView> query(NotificationFindAllQuery query){
		System.out.println(this.getClass() + " - query(NotificationFindAllQuery)");
		return Multiple.of(new NotificationView(null, null));
	}
}
