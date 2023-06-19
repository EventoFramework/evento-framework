package org.evento.server.service.deploy;

import org.evento.common.messaging.bus.MessageBus;
import org.evento.server.domain.model.Bundle;
import org.evento.server.domain.repository.BundleRepository;
import org.springframework.integration.support.locks.LockRegistry;


public class DockerBundleDeployService extends BundleDeployService {
	public DockerBundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleRepository bundleRepository) {
		super(messageBus, lockRegistry, bundleRepository);
	}

	@Override
	protected void spawn(Bundle bundle) throws Exception {

	}
}
