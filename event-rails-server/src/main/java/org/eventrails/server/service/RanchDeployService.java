package org.eventrails.server.service;

import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class RanchDeployService {

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
			if(s != null)
				s.release();
		});
	}

	public void waitUntilAvailable(String ranchName) {

		if(!messageBus.isRanchAvailable(ranchName)){
			var ranch = ranchApplicationService.findByName(ranchName);
			var lock = lockRegistry.obtain("RANCH:" + ranchName);
			try
			{
				lock.lock();
				var semaphore = semaphoreMap.getOrDefault(ranchName, new Semaphore(0));
				semaphoreMap.put(ranchName, semaphore);
				switch (ranch.getBucketType()){
					case LocalFilesystem -> {
						Runtime.getRuntime()
								.exec("cmd /c start cmd.exe /K \""+javaExe+" -jar "+ranch.getArtifactCoordinates()+"\"");
						if(!semaphore.tryAcquire(30, TimeUnit.SECONDS)){
							throw new IllegalStateException("Ranch Cannot Start");
						}
					}
					default -> {
						throw new IllegalArgumentException("Bucket Type Not Implemented");
					}
				}

			} catch (IOException | InterruptedException e)
			{
				throw new RuntimeException(e);
			} finally
			{
				semaphoreMap.remove(ranchName);
				lock.unlock();
			}
		}
	}
}
