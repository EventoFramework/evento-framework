package com.evento.server.service.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.evento.server.domain.model.core.BucketType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.repository.core.BundleRepository;
import com.evento.server.web.config.AuthService;
import com.evento.server.web.config.TokenRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * The BundleDeployService class is responsible for deploying bundles.
 */
@Component
public class BundleDeployService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BundleDeployService.class);

	private final BundleRepository bundleRepository;

	@Value("${evento.deploy.spawn.script}")
	private String spawnScript;

	private final AuthService authService;


	/**
	 * The BundleDeployService class is responsible for deploying bundles.
	 */
	public BundleDeployService(BundleRepository bundleRepository, AuthService authService) {
		this.bundleRepository = bundleRepository;
		this.authService = authService;
	}

	/**
	 * The SyncPipe class represents a synchronized pipe for streaming data from an input stream to an output stream.
	 */
	static class SyncPipe implements Runnable
	{
		/**
		 * The SyncPipe class represents a synchronized pipe for streaming data from an input stream to an output stream.
		 */
		public SyncPipe(InputStream inputStream, OutputStream outputStream) {
			this.inputStream = inputStream;
			this.outputStream = outputStream;
		}
		/**
		 * This method represents the main functionality of a synchronized pipe for streaming data from an input stream to an output stream.
		 * It reads data from the input stream and writes it to the output stream.
		 * If any error occurs during the process, it logs the exception with a specific error message.
		 */
		public void run() {
			try
			{
				final byte[] buffer = new byte[1024];
				for (int length; (length = inputStream.read(buffer)) != -1; )
				{
					outputStream.write(buffer, 0, length);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Pipe Error", e);
			}
		}
		private final OutputStream outputStream;
		private final InputStream inputStream;
	}

	/**
	 * Spawns a bundle by executing a spawn script.
	 *
	 * @param bundle The bundle to be spawned.
	 * @throws Exception If an error occurs during the spawn process.
	 */
	public void spawn(Bundle bundle) throws Exception {
		if(bundle.getBucketType() == BucketType.Ephemeral){
			throw new IllegalArgumentException("Cannot spawn an Ephemeral bundle");
		}
		if(!bundle.isDeployable()){
			throw new IllegalArgumentException("Cannot spawn an Non Deployable Bundle");
		}
		var mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		Process p = Runtime.getRuntime().exec(new String[]{spawnScript ,
				mapper.writeValueAsString(bundle),
				authService.generateJWT("evento-server-deploy", new TokenRole[]{TokenRole.ROLE_DEPLOY}, 1000 * 60)});
		var iT = new Thread(new SyncPipe(p.getInputStream(), System.out));
		iT.setName("Deploy Output reader thread");
		iT.start();
		var oT = new Thread(new SyncPipe(p.getErrorStream(), System.err));
		oT.setName("Deploy Error reader thread");
		oT.start();
		p.waitFor();
	}

	/**
	 * Spawns a bundle by executing a spawn script.
	 *
	 * @param bundleId The ID of the bundle to be spawned.
	 * @throws Exception If an error occurs during the spawn process.
	 */
	public void spawn(String bundleId) throws Exception {
		spawn(bundleRepository.findById(bundleId).orElseThrow());
	}

}
