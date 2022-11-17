package org.eventrails.server.service.deploy;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.server.service.BundleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.support.locks.LockRegistry;

@Configuration
public class BundleDeployServiceConfiguration {

	@Bean
	public BundleDeployService bundleDeployService(
			MessageBus messageBus, LockRegistry lockRegistry, BundleService bundleService,
			@Value("${eventrails.cluster.bundle.deploy.service}") String bundleDeployService,
			@Value("${eventrails.cluster.bundle.deploy.java:null}") String javaExe){
		return switch (bundleDeployService)
				{
					case "docker" -> new DockerBundleDeployService(messageBus, lockRegistry, bundleService);
					case "local-docker" -> new LocalDockerBundleDeployService(messageBus, lockRegistry, bundleService);
					default -> new LocalMachineBundleDeployService(messageBus, lockRegistry, bundleService, javaExe);
				};
	}
}
