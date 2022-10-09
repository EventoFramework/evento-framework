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
import org.eventrails.modeling.messaging.query.Single;

@Projection
public class DemoProjection {

	@QueryHandler
	Single<DemoView> query(DemoViewFindByIdQuery query, QueryMessage<DemoViewFindByIdQuery> queryMessage) {

		System.out.println(this.getClass() + " - query(DemoViewFindByIdQuery)");
		return Single.of(new DemoView(null,
				null, 0));
	}

	@QueryHandler
	Single<DemoRichView> queryRich(DemoRichViewFindByIdQuery query) {
		System.out.println(this.getClass() + " - query(DemoRichViewFindByIdQuery)");
		return Single.of(new DemoRichView(null, null, 0, null, null));
	}

	@QueryHandler
	Multiple<DemoView> query(DemoViewFindAllQuery query) {
		System.out.println(this.getClass() + " - query(DemoViewFindAllQuery)");
		return Multiple.of(new DemoView(null, null, 0));
	}

	@QueryHandler
	Multiple<DemoRichView> queryRich(DemoRichViewFindAllQuery query) {
		System.out.println(this.getClass() + " - query(DemoRichViewFindAllQuery)");
		return Multiple.of(new DemoRichView("demo1", null, 0, null, null),new DemoRichView("demo2", null, 0, null, null));
	}
}
