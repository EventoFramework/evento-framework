package org.eventrails.server.service.deploy;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.annotations.component.Service;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.service.BundleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.support.locks.LockRegistry;


public class DockerBundleDeployService extends BundleDeployService{
	public DockerBundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleService bundleService) {
		super(messageBus, lockRegistry, bundleService);
	}

	@Override
	protected void spawn(Bundle bundle) throws Exception {

	}
}
