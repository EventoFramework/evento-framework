package org.eventrails.server.service.deploy;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.service.BundleService;
import org.springframework.integration.support.locks.LockRegistry;


public class LocalDockerBundleDeployService extends BundleDeployService{
	public LocalDockerBundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleService bundleService) {
		super(messageBus, lockRegistry, bundleService);
	}

	@Override
	protected void spawn(Bundle bundle) throws Exception {

	}
}
