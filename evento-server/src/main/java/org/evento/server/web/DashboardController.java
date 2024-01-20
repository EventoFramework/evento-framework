package org.evento.server.web;

import org.evento.server.bus.MessageBus;
import org.evento.server.bus.NodeAddress;
import org.evento.server.domain.repository.core.BundleRepository;
import org.evento.server.domain.repository.core.ComponentRepository;
import org.evento.server.domain.repository.core.PayloadRepository;
import org.evento.server.es.EventStore;
import org.evento.server.web.dto.DashboardDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/dashboard")
public class DashboardController {

	private final PayloadRepository payloadRepository;
	private final ComponentRepository componentRepository;
	private final BundleRepository bundleRepository;
	private final MessageBus messageBus;
	private final EventStore eventStore;
	@Value("${evento.cluster.node.server.id}")
	private String serverNodeName;

	public DashboardController(PayloadRepository payloadRepository, ComponentRepository componentRepository, BundleRepository bundleRepository, MessageBus messageBus, EventStore eventStore) {
		this.payloadRepository = payloadRepository;
		this.componentRepository = componentRepository;
		this.bundleRepository = bundleRepository;
		this.messageBus = messageBus;
		this.eventStore = eventStore;
	}

	@GetMapping()
	@Secured("ROLE_WEB")
	public DashboardDTO getDashboard() {
		var db = new DashboardDTO();

		db.setServerName(serverNodeName);

		db.setPayloadCount(payloadRepository.count());
		db.setPayloadCountByType(payloadRepository.countByType());

		db.setComponentCount(componentRepository.count());
		db.setComponentCountByType(componentRepository.countByType());

		db.setBundleCount(bundleRepository.count());
		db.setDeployableBundleCount(bundleRepository.countDeployable());
		var view = messageBus.getCurrentAvailableView();
		db.setBundleInViewCount(view.stream()
				.map(NodeAddress::bundleId).distinct().count());
		db.setNodeInViewCount(view.size());

		db.setEventCount(eventStore.getSize());
		db.setAggregateCount(eventStore.getAggregateCount());
		db.setEventPublicationFrequency(eventStore.getRecentPublicationRation());
		return db;
	}
}
