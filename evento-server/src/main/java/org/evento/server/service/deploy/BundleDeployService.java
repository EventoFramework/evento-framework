package org.evento.server.service.deploy;

import org.evento.server.bus.MessageBus;
import org.evento.server.domain.model.Bundle;
import org.evento.server.domain.repository.BundleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class BundleDeployService {

	private final Logger LOGGER = LoggerFactory.getLogger(BundleDeployService.class);


	private final MessageBus messageBus;
	private final LockRegistry lockRegistry;
	private final BundleRepository bundleRepository;

	private final ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

	public BundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleRepository bundleRepository) {
		this.messageBus = messageBus;
		this.lockRegistry = lockRegistry;
		this.bundleRepository = bundleRepository;
		messageBus.addJoinListener(bundle -> {
			synchronized (semaphoreMap) {
				var s = semaphoreMap.get(bundle.getBundleId());
				if (s != null)
					s.release();
			}
		});
	}

	public void waitUntilAvailable(String bundleId) {

		if (!messageBus.isBundleAvailable(bundleId))
		{
			LOGGER.info("Bundle %s not available, spawning a new one".formatted(bundleId));
			var bundle = bundleRepository.findById(bundleId).orElseThrow();
			var lock = lockRegistry.obtain("BUNDLE:" + bundleId);
			try
			{
				lock.lock();
				var semaphore = semaphoreMap.getOrDefault(bundleId, new Semaphore(0));
				semaphoreMap.put(bundleId, semaphore);
				if (messageBus.isBundleAvailable(bundleId)) return;
				spawn(bundle);
				if (!semaphore.tryAcquire(120, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Bundle Cannot Start");
				}
				LOGGER.info("New %s bundle spawned".formatted(bundleId));

			} catch (Exception e)
			{
				LOGGER.error("Spawning for %s bundle failed".formatted(bundleId), e);
				throw new RuntimeException(e);
			} finally
			{
				semaphoreMap.remove(bundleId);
				lock.unlock();
			}
		}
	}

	protected void spawn(Bundle bundle) throws Exception {

	};

	public void spawn(String bundleId) throws Exception {
		spawn(bundleRepository.findById(bundleId).orElseThrow());
	}

	public void kill(String nodeId) throws Exception {
		LOGGER.info("Sending kill signal to {}", nodeId);
		messageBus.sendKill(nodeId);
	}
}
