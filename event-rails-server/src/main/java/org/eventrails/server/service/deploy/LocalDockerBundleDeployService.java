package org.eventrails.server.service.deploy;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.service.BundleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;

import java.time.Instant;


public class LocalDockerBundleDeployService extends BundleDeployService{

	private final Logger LOGGER = LoggerFactory.getLogger(LocalDockerBundleDeployService.class);
	public LocalDockerBundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleService bundleService) {
		super(messageBus, lockRegistry, bundleService);
	}

	@Override
	protected void spawn(Bundle bundle) throws Exception {
		LOGGER.info("Spawning bundle {}", bundle.getName());
		var cmd = new String[]{
				"docker",
				"run",
				"--name", bundle.getName() + "-" + Instant.now().toEpochMilli(),
				"-d",
				"--rm",
				"-e",
				"APP_JAR_URL=http://host.docker.internal:3000/asset/bundle/" + bundle.getName(),
				"eventrails-bundle-container"
		};
		LOGGER.info("COMMAND: {}", String.join(" ", cmd));
		Runtime.getRuntime()
				.exec(cmd);
	}
}
