package org.eventrails.demo.query;

import org.eventrails.demo.api.query.DemoRichViewFindAllQuery;
import org.eventrails.demo.api.query.DemoRichViewFindByIdQuery;
import org.eventrails.demo.api.query.DemoViewFindAllQuery;
import org.eventrails.demo.api.query.DemoViewFindByIdQuery;
import org.eventrails.demo.api.view.DemoRichView;
import org.eventrails.demo.api.view.DemoView;
import org.eventrails.modeling.annotations.component.Projection;
import org.eventrails.modeling.annotations.handler.QueryHandler;
import org.eventrails.modeling.messaging.message.QueryMessage;
import org.eventrails.modeling.messaging.query.Multiple;

@Projection
public class DemoProjection {

	@QueryHandler
	DemoView query(DemoViewFindByIdQuery query, QueryMessage queryMessage) {
		return new DemoView(null,
				null, 0);
	}

	@QueryHandler
	DemoRichView queryRich(DemoRichViewFindByIdQuery query) {
		return new DemoRichView(null, null, 0, null, null);
	}

	@QueryHandler
	Multiple<DemoView> query(DemoViewFindAllQuery query) {
		return Multiple.of(new DemoView(null, null, 0));
	}

	@QueryHandler
	Multiple<DemoRichView> queryRich(DemoRichViewFindAllQuery query) {
		return Multiple.of(new DemoRichView(null, null, 0, null, null));
	}
}
