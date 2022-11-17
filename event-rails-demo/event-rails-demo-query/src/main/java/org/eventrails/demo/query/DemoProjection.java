package org.eventrails.demo.query;

import org.eventrails.common.utils.Inject;
import org.eventrails.demo.api.query.DemoRichViewFindAllQuery;
import org.eventrails.demo.api.query.DemoRichViewFindByIdQuery;
import org.eventrails.demo.api.query.DemoViewFindAllQuery;
import org.eventrails.demo.api.query.DemoViewFindByIdQuery;
import org.eventrails.demo.api.utils.Utils;
import org.eventrails.demo.api.view.DemoRichView;
import org.eventrails.demo.api.view.DemoView;
import org.eventrails.common.modeling.annotations.component.Projection;
import org.eventrails.common.modeling.annotations.handler.QueryHandler;
import org.eventrails.common.modeling.messaging.message.application.QueryMessage;
import org.eventrails.common.modeling.messaging.query.Multiple;
import org.eventrails.common.modeling.messaging.query.Single;
import org.eventrails.demo.query.domain.Demo;
import org.eventrails.demo.query.domain.DemoRepository;

@Projection
public class DemoProjection {

	@Inject
	private DemoRepository demoRepository;

	@QueryHandler
	Single<DemoView> query(DemoViewFindByIdQuery query, QueryMessage<DemoViewFindByIdQuery> queryMessage) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoRepository.findById(query.getDemoId()).orElseThrow().toDemoView();
		Utils.logMethodFlow(this,"query", query, "END");
		return Single.of(result);
	}

	@QueryHandler
	Single<DemoRichView> queryRich(DemoRichViewFindByIdQuery query) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoRepository.findById(query.getDemoId()).orElseThrow().toDemoRichView();
		Utils.logMethodFlow(this,"query", query, "END");
		return Single.of(result);
	}

	@QueryHandler
	Multiple<DemoView> query(DemoViewFindAllQuery query) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoRepository.findAll().stream().map(Demo::toDemoView).toList();
		Utils.logMethodFlow(this,"query", query, "END");
		return Multiple.of(result);
	}

	@QueryHandler
	Multiple<DemoRichView> queryRich(DemoRichViewFindAllQuery query) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoRepository.findAll().stream().map(Demo::toDemoRichView).toList();
		Utils.logMethodFlow(this,"query", query, "END");
		return Multiple.of(result);
	}
}
