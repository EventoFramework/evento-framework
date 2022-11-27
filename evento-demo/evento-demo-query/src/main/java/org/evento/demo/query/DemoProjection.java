package org.evento.demo.query;

import org.evento.common.utils.Inject;
import org.evento.demo.api.query.DemoRichViewFindAllQuery;
import org.evento.demo.api.query.DemoRichViewFindByIdQuery;
import org.evento.demo.api.query.DemoViewFindAllQuery;
import org.evento.demo.api.query.DemoViewFindByIdQuery;
import org.evento.demo.api.utils.Utils;
import org.evento.demo.api.view.DemoRichView;
import org.evento.demo.api.view.DemoView;
import org.evento.common.modeling.annotations.component.Projection;
import org.evento.common.modeling.annotations.handler.QueryHandler;
import org.evento.common.modeling.messaging.message.application.QueryMessage;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.modeling.messaging.query.Single;
import org.evento.demo.query.domain.mongo.DemoMongo;
import org.evento.demo.query.domain.mongo.DemoMongoRepository;

@Projection
public class DemoProjection {

	@Inject
	private DemoMongoRepository demoMongoRepository;

	@QueryHandler
	Single<DemoView> query(DemoViewFindByIdQuery query, QueryMessage<DemoViewFindByIdQuery> queryMessage) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoMongoRepository.findById(query.getDemoId()).filter(d -> d.getDeletedAt() != null).orElseThrow().toDemoView();
		Utils.logMethodFlow(this,"query", query, "END");
		return Single.of(result);
	}

	@QueryHandler
	Single<DemoRichView> queryRich(DemoRichViewFindByIdQuery query) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoMongoRepository.findById(query.getDemoId()).filter(d -> d.getDeletedAt() != null).orElseThrow().toDemoRichView();
		Utils.logMethodFlow(this,"query", query, "END");
		return Single.of(result);
	}

	@QueryHandler
	Multiple<DemoView> query(DemoViewFindAllQuery query) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoMongoRepository.findAll().stream().filter(d -> d.getDeletedAt() != null).map(DemoMongo::toDemoView).toList();
		Utils.logMethodFlow(this,"query", query, "END");
		return Multiple.of(result);
	}

	@QueryHandler
	Multiple<DemoRichView> queryRich(DemoRichViewFindAllQuery query) {
		Utils.logMethodFlow(this,"query", query, "BEGIN");
		var result = demoMongoRepository.findAll().stream().filter(d -> d.getDeletedAt() != null).map(DemoMongo::toDemoRichView).toList();
		Utils.logMethodFlow(this,"query", query, "END");
		return Multiple.of(result);
	}
}
