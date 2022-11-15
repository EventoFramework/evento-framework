package org.eventrails.server.service.deploy;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.service.BundleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public abstract class BundleDeployService {

	private final Logger LOGGER = LoggerFactory.getLogger(BundleDeployService.class);


	private final MessageBus messageBus;
	private final LockRegistry lockRegistry;
	private final BundleService bundleService;

	private final ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

	public BundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleService bundleService) {
		this.messageBus = messageBus;
		this.lockRegistry = lockRegistry;
		this.bundleService = bundleService;
		messageBus.addJoinListener(bundle -> {
			var s = semaphoreMap.get(bundle.getNodeName());
			if (s != null)
				s.release();
		});
	}

	public void waitUntilAvailable(String bundleName) {

		if (!messageBus.isBundleAvailable(bundleName))
		{
			var bundle = bundleService.findByName(bundleName);
			var lock = lockRegistry.obtain("RANCH:" + bundleName);
			try
			{
				lock.lock();
				var semaphore = semaphoreMap.getOrDefault(bundleName, new Semaphore(0));
				semaphoreMap.put(bundleName, semaphore);
				if(messageBus.isBundleAvailable(bundleName)) return;
				spawn(bundle);
				if (!semaphore.tryAcquire(60, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Bundle Cannot Start");
				}

			} catch (Exception e)
			{
				throw new RuntimeException(e);
			} finally
			{
				semaphoreMap.remove(bundleName);
				lock.unlock();
			}
		}
	}

	protected abstract void spawn(Bundle bundle) throws Exception;

	public void spawn(String bundleName) throws Exception {
		spawn(bundleService.findByName(bundleName));
	}

	public void kill(String nodeId) throws Exception {
		LOGGER.info("Sending kill signal to {}", nodeId);
		messageBus.sendKill(nodeId);
	}
}
