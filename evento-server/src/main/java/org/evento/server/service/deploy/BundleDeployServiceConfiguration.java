package org.evento.server.service.deploy;

import org.evento.common.messaging.bus.MessageBus;
import org.evento.server.domain.repository.BundleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.support.locks.LockRegistry;

@Configuration
public class BundleDeployServiceConfiguration {

	@Bean
	public BundleDeployService bundleDeployService(
			MessageBus messageBus, LockRegistry lockRegistry, BundleRepository bundleRepository,
			@Value("${evento.cluster.bundle.deploy.service}") String bundleDeployService,
			@Value("${evento.cluster.bundle.deploy.java:null}") String javaExe) {
		return switch (bundleDeployService)
		{
			case "docker" -> new DockerBundleDeployService(messageBus, lockRegistry, bundleRepository);
			case "local-docker" -> new LocalDockerBundleDeployService(messageBus, lockRegistry, bundleRepository);
			default -> new LocalMachineBundleDeployService(messageBus, lockRegistry, bundleRepository, javaExe);
		};
	}
}
