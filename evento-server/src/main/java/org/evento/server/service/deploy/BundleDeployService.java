package org.evento.server.service.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.evento.server.domain.model.core.BucketType;
import org.evento.server.domain.model.core.Bundle;
import org.evento.server.domain.repository.core.BundleRepository;
import org.evento.server.web.config.AuthService;
import org.evento.server.web.config.TokenRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;

@Component
public class BundleDeployService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BundleDeployService.class);

	private final BundleRepository bundleRepository;

	@Value("${evento.deploy.spawn.script}")
	private String spawnScript;

	private final AuthService authService;


	public BundleDeployService(BundleRepository bundleRepository, AuthService authService) {
		this.bundleRepository = bundleRepository;
		this.authService = authService;
	}

	static class SyncPipe implements Runnable
	{
		public SyncPipe(InputStream istrm, OutputStream ostrm) {
			istrm_ = istrm;
			ostrm_ = ostrm;
		}
		public void run() {
			try
			{
				final byte[] buffer = new byte[1024];
				for (int length = 0; (length = istrm_.read(buffer)) != -1; )
				{
					ostrm_.write(buffer, 0, length);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Pipe Error", e);
			}
		}
		private final OutputStream ostrm_;
		private final InputStream istrm_;
	}

	public void spawn(Bundle bundle) throws Exception {
		if(bundle.getBucketType() == BucketType.Ephemeral){
			throw new IllegalArgumentException("Cannot spawn an Ephemeral bundle");
		}
		var mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		Process p = Runtime.getRuntime().exec(spawnScript + " \"" +
				mapper.writeValueAsString(bundle).replace("\"","\\\"") + "\" " +
				authService.generateJWT("evento-server-deploy", new TokenRole[]{TokenRole.ROLE_DEPLOY}, 1000 * 60));
		new Thread(new SyncPipe(p.getInputStream(), System.out)).start();
		new Thread(new SyncPipe(p.getErrorStream(), System.err)).start();
		p.waitFor();
	}

	public void spawn(String bundleId) throws Exception {
		spawn(bundleRepository.findById(bundleId).orElseThrow());
	}

}
