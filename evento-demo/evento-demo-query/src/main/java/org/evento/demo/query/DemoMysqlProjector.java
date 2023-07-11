package org.evento.demo.query;

import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.annotations.component.Projector;
import org.evento.common.modeling.annotations.handler.EventHandler;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.demo.api.event.DemoCreatedEvent;
import org.evento.demo.api.event.DemoDeletedEvent;
import org.evento.demo.api.event.DemoUpdatedEvent;
import org.evento.demo.api.utils.Utils;
import org.evento.demo.query.domain.mysql.DemoMysql;
import org.evento.demo.query.domain.mysql.DemoMysqlRepository;

import java.time.Instant;

@Projector(version = 3)
public class DemoMysqlProjector {

	private final DemoMysqlRepository demoMysqlRepository;

	public DemoMysqlProjector(DemoMysqlRepository demoMysqlRepository) {
		this.demoMysqlRepository = demoMysqlRepository;
	}

	@EventHandler
	void on(DemoCreatedEvent event, QueryGateway queryGateway, EventMessage eventMessage) {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		var now = Instant.now();
		demoMysqlRepository.save(new DemoMysql(event.getDemoId(), event.getName(),
				event.getValue(), now, now, null));
		Utils.logMethodFlow(this, "on", event, "END");
	}

	@EventHandler
	void on(DemoUpdatedEvent event) {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		var now = Instant.now();
		demoMysqlRepository.findById(event.getDemoId()).ifPresent(d -> {
			d.setName(event.getName());
			d.setValue(event.getValue());
			d.setUpdatedAt(Instant.now());
			demoMysqlRepository.save(d);
		});
		Utils.logMethodFlow(this, "on", event, "END");
		System.out.println("Getch and save in " + (Instant.now().toEpochMilli() - now.toEpochMilli()));

	}

	@EventHandler
	void on(DemoDeletedEvent event) {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		demoMysqlRepository.findById(event.getDemoId()).ifPresent(d -> {
			d.setDeletedAt(Instant.now());
			demoMysqlRepository.save(d);
		});
		Utils.logMethodFlow(this, "on", event, "END");

	}
}
