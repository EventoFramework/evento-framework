package com.evento.demo.query;

import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.demo.api.event.DemoCreatedEvent;
import com.evento.demo.api.event.DemoDeletedEvent;
import com.evento.demo.api.event.DemoUpdatedEvent;
import com.evento.demo.api.utils.Utils;
import com.evento.demo.query.domain.Demo;
import com.evento.demo.query.domain.DemoRepository;

import java.time.Instant;

@Projector(version = 3)
public class DemoProjector {

	private final DemoRepository demoRepository;

	public DemoProjector(DemoRepository demoRepository) {
		this.demoRepository = demoRepository;
	}

	@EventHandler
	void on(DemoCreatedEvent event,
			QueryGateway queryGateway,
			EventMessage<?> eventMessage,
			Metadata metadata,
			Instant instant) {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		var now = Instant.now();
		demoRepository.save(new Demo(event.getDemoId(), event.getName(),
				event.getValue(), now, now, null));
		Utils.logMethodFlow(this, "on", event, "END");
	}

	@EventHandler
	void on(DemoUpdatedEvent event) {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		var now = Instant.now();
		demoRepository.findById(event.getDemoId()).ifPresent(d -> {
			d.setName(event.getName());
			d.setValue(event.getValue());
			d.setUpdatedAt(Instant.now());
			demoRepository.save(d);
		});
		Utils.logMethodFlow(this, "on", event, "END");

	}

	@EventHandler
	void on(DemoDeletedEvent event) {
		Utils.logMethodFlow(this, "on", event, "BEGIN");
		demoRepository.findById(event.getDemoId()).ifPresent(d -> {
			d.setDeletedAt(Instant.now());
			demoRepository.save(d);
		});
		Utils.logMethodFlow(this, "on", event, "END");

	}
}
