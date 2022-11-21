package org.eventrails.demo.query;

import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.annotations.component.Projector;
import org.eventrails.common.modeling.annotations.handler.EventHandler;
import org.eventrails.common.modeling.bundle.TransactionalProjector;
import org.eventrails.common.modeling.messaging.message.application.EventMessage;
import org.eventrails.common.utils.Inject;
import org.eventrails.demo.api.event.DemoCreatedEvent;
import org.eventrails.demo.api.event.DemoDeletedEvent;
import org.eventrails.demo.api.event.DemoUpdatedEvent;
import org.eventrails.demo.api.utils.Utils;
import org.eventrails.demo.query.domain.mongo.DemoMongo;
import org.eventrails.demo.query.domain.mongo.DemoMongoRepository;
import org.eventrails.demo.query.domain.mysql.DemoMysql;
import org.eventrails.demo.query.domain.mysql.DemoMysqlRepository;

import java.time.Instant;

@Projector
public class DemoMysqlProjector implements TransactionalProjector {
	@Inject
	private DemoMysqlRepository demoMysqlRepository;

	@EventHandler
	void on(DemoCreatedEvent event, QueryGateway queryGateway, EventMessage eventMessage){
		Utils.logMethodFlow(this,"on", event, "BEGIN");
		var now = Instant.now();
		demoMysqlRepository.save(new DemoMysql(event.getDemoId(), event.getName(), event.getValue(),now, now , null));
		Utils.logMethodFlow(this,"on", event, "END");


	}

	@EventHandler
	void on(DemoUpdatedEvent event){
		Utils.logMethodFlow(this,"on", event, "BEGIN");
		demoMysqlRepository.findById(event.getDemoId()).ifPresent(d -> {
			d.setName(event.getName());
			d.setValue(event.getValue());
			d.setUpdatedAt(Instant.now());
			demoMysqlRepository.save(d);
		});
		Utils.logMethodFlow(this,"on", event, "END");

	}

	@EventHandler
	void on(DemoDeletedEvent event){
		Utils.logMethodFlow(this,"on", event, "BEGIN");
		demoMysqlRepository.findById(event.getDemoId()).ifPresent(d -> {
			d.setDeletedAt(Instant.now());
			demoMysqlRepository.save(d);
		});
		Utils.logMethodFlow(this,"on", event, "END");

	}


	@Override
	public void begin() throws Exception {

	}

	@Override
	public void commit() throws Exception {

	}

	@Override
	public void rollback() throws Exception {

	}
}
