package org.eventrails.server.service.deploy;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.service.BundleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;

import java.time.Instant;
import java.util.ArrayList;


public class LocalDockerBundleDeployService extends BundleDeployService {

	private final Logger LOGGER = LoggerFactory.getLogger(LocalDockerBundleDeployService.class);

	public LocalDockerBundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleRepository bundleRepository) {
		super(messageBus, lockRegistry, bundleRepository);
	}

	@Override
	protected void spawn(Bundle bundle) throws Exception {
		LOGGER.info("Spawning bundle {}", bundle.getId());
		var cmd = new ArrayList<String>();
		cmd.add("docker");
		cmd.add("run");
		cmd.add("--name");
		cmd.add(bundle.getId() + "-" + Instant.now().toEpochMilli());
		cmd.add("-d");
		cmd.add("--rm");
		cmd.add("-e");
		cmd.add("APP_JAR_URL=http://host.docker.internal:3000/asset/bundle/" + bundle.getId());
		bundle.getEnvironment().forEach((k, v) -> {
			cmd.add("-e");
			cmd.add(k + "=" + v);
		});
		cmd.add("eventrails-bundle-container");


		LOGGER.info("COMMAND: {}", String.join(" ", cmd));
		Runtime.getRuntime()
				.exec(cmd.toArray(String[]::new));
	}
}
