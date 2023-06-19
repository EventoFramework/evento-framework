package org.evento.server.service.deploy;

import org.evento.common.messaging.bus.MessageBus;
import org.evento.server.domain.model.Bundle;
import org.evento.server.domain.repository.BundleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.locks.LockRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class LocalMachineBundleDeployService extends BundleDeployService {

	private final Logger LOGGER = LoggerFactory.getLogger(LocalMachineBundleDeployService.class);


	private String javaExe;

	public LocalMachineBundleDeployService(MessageBus messageBus, LockRegistry lockRegistry, BundleRepository bundleRepository,
										   String javaExe) {
		super(messageBus, lockRegistry, bundleRepository);
		this.javaExe = javaExe;
	}

	protected void spawn(Bundle bundle) throws Exception {
		LOGGER.info("Spawning bundle {}", bundle.getId());
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
						LOGGER.debug("[" + bundle.getId() + " (" + p.pid() + ")]: " + line);
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
						LOGGER.error("[" + bundle.getId() + " (" + p.pid() + ")]: " + line);
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
							LOGGER.error("[" + bundle.getId() + " (" + p.pid() + ")]: TERMINATED WITH ERROR");
						} else
						{
							LOGGER.debug("[" + bundle.getId() + " (" + p.pid() + ")]: TERMINATED GRACEFULLY");
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
}
