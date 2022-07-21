package org.eventrails.demo.query;

import org.eventrails.demo.api.query.DemoFindAllQuery;
import org.eventrails.demo.api.query.DemoFindByIdQuery;
import org.eventrails.demo.api.view.DemoRichView;
import org.eventrails.demo.api.view.DemoView;
import org.eventrails.modeling.annotations.component.Projection;
import org.eventrails.modeling.annotations.handler.QueryHandler;
import org.eventrails.modeling.messaging.QueryMessage;
import org.eventrails.modeling.messaging.query.Multiple;

import java.util.List;

@Projection
public class DemoProjection {

	@QueryHandler
	DemoView query(DemoFindByIdQuery query, QueryMessage queryMessage) {
		return new DemoView(null,
				null, 0);
	}

	@QueryHandler
	DemoRichView queryRich(DemoFindByIdQuery query) {
		return new DemoRichView(null, null, 0, null, null);
	}

	@QueryHandler
	Multiple<DemoView> query(DemoFindAllQuery query) {
		return Multiple.of(new DemoView(null, null, 0));
	}

	@QueryHandler
	Multiple<DemoRichView> queryRich(DemoFindAllQuery query) {
		return Multiple.of(new DemoRichView(null, null, 0, null, null));
	}
}
