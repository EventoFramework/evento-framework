package org.eventrails.server.service;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.server.domain.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class BundleDeployService {

	private final Logger LOGGER = LoggerFactory.getLogger(BundleDeployService.class);

	@Value("${eventrails.bundle.deploy.java}")
	private String javaExe;
	private final MessageBus messageBus;
	private final LockRegistry lockRegistry;
	private final BundleService bundleService;

	private final ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

	public BundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleService bundleService) {
		this.messageBus = messageBus;
		this.lockRegistry = lockRegistry;
		this.bundleService = bundleService;
		messageBus.addJoinListener(bundle -> {
			var s = semaphoreMap.get(bundle);
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

	public void spawn(Bundle bundle) throws Exception {
		LOGGER.info("Spawning bundle {}", bundle.getName());
		switch (bundle.getBucketType())
		{
			case LocalFilesystem ->
			{
				var p = Runtime.getRuntime()
						.exec(new String[]{javaExe, "-jar", bundle.getArtifactCoordinates()});
				var t1 = new Thread(() -> {
					BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
					String line;
					while (true)
					{
						try
						{
							if (!((line = input.readLine()) != null)) break;
						} catch (IOException e)
						{
							throw new RuntimeException(e);
						}
						LOGGER.debug("[" + bundle.getName() + " (" + p.pid() + ")]: " + line);
					}
				});
				var t2 = new Thread(() -> {
					BufferedReader input = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					String line;
					while (true)
					{
						try
						{
							if (!((line = input.readLine()) != null)) break;
						} catch (IOException e)
						{
							throw new RuntimeException(e);
						}
						LOGGER.error("[" + bundle.getName() + " (" + p.pid() + ")]: " + line);
					}
				});
				t1.start();
				t2.start();
				new Thread(() -> {
					try
					{
						int exitCode = p.waitFor();
						if (exitCode != 0)
						{
							LOGGER.error("[" + bundle.getName() + " (" + p.pid() + ")]: TERMINATED WITH ERROR");
						} else
						{
							LOGGER.debug("[" + bundle.getName() + " (" + p.pid() + ")]: TERMINATED GRACEFULLY");
						}
					} catch (InterruptedException e)
					{
						throw new RuntimeException(e);
					}
				}).start();

			}
			default ->
			{
				throw new IllegalArgumentException("Bucket Type Not Implemented");
			}
		}
	}

	public void spawn(String bundleName) throws Exception {
		spawn(bundleService.findByName(bundleName));
	}

	public void kill(String nodeId) throws Exception {
		LOGGER.info("Sending kill signal to {}", nodeId);
		messageBus.sendKill(nodeId);
	}
}
