package org.evento.server.service.deploy;

import org.evento.server.domain.model.core.Bundle;
import org.evento.server.domain.repository.core.BundleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BundleDeployService {

	private final Logger LOGGER = LoggerFactory.getLogger(BundleDeployService.class);

	private final BundleRepository bundleRepository;


	public BundleDeployService(BundleRepository bundleRepository) {
		this.bundleRepository = bundleRepository;
	}



	public void spawn(Bundle bundle) throws Exception {

	};

	public void spawn(String bundleId) throws Exception {
		spawn(bundleRepository.findById(bundleId).orElseThrow());
	}

	public void kill(String nodeId) throws Exception {
		LOGGER.info("Sending kill signal to {}", nodeId);
		// messageBus.sendKill(nodeId);
	}
}
