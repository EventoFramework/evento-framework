package org.eventrails.server.service;

import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.server.domain.model.Ranch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class RanchDeployService {

	private final Logger LOGGER = LoggerFactory.getLogger(RanchDeployService.class);

	@Value("${eventrails.ranch.deploy.java}")
	private String javaExe;
	private final MessageBus messageBus;
	private final LockRegistry lockRegistry;
	private final RanchApplicationService ranchApplicationService;

	private final HashMap<String, Semaphore> semaphoreMap = new HashMap<>();

	public RanchDeployService(MessageBus messageBus, LockRegistry lockRegistry, RanchApplicationService ranchApplicationService) {
		this.messageBus = messageBus;
		this.lockRegistry = lockRegistry;
		this.ranchApplicationService = ranchApplicationService;
		messageBus.addJoinListener(ranch -> {
			var s = semaphoreMap.get(ranch);
			if (s != null)
				s.release();
		});
	}

	public void waitUntilAvailable(String ranchName) {

		if (!messageBus.isRanchAvailable(ranchName))
		{
			var ranch = ranchApplicationService.findByName(ranchName);
			var lock = lockRegistry.obtain("RANCH:" + ranchName);
			try
			{
				lock.lock();
				var semaphore = semaphoreMap.getOrDefault(ranchName, new Semaphore(0));
				semaphoreMap.put(ranchName, semaphore);
				spawn(ranch);
				if (!semaphore.tryAcquire(30, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Ranch Cannot Start");
				}

			} catch (Exception e)
			{
				throw new RuntimeException(e);
			} finally
			{
				semaphoreMap.remove(ranchName);
				lock.unlock();
			}
		}
	}

	public void spawn(Ranch ranch) throws Exception {
		LOGGER.info("Spawning ranch {}", ranch.getName());
		switch (ranch.getBucketType())
		{
			case LocalFilesystem ->
			{
				var p = Runtime.getRuntime()
						.exec(new String[]{javaExe, "-jar", ranch.getArtifactCoordinates()});
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
						LOGGER.debug("[" + ranch.getName() + " (" + p.pid() + ")]: " + line);
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
						LOGGER.error("[" + ranch.getName() + " (" + p.pid() + ")]: " + line);
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
							LOGGER.error("[" + ranch.getName() + " (" + p.pid() + ")]: TERMINATED WITH ERROR");
						} else
						{
							LOGGER.debug("[" + ranch.getName() + " (" + p.pid() + ")]: TERMINATED GRACEFULLY");
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

	public void spawn(String ranchName) throws Exception {
		spawn(ranchApplicationService.findByName(ranchName));
	}

	public void kill(String nodeId) throws Exception {
		LOGGER.info("Sending kill signal to {}", nodeId);
		messageBus.sendKill(nodeId);
	}
}
