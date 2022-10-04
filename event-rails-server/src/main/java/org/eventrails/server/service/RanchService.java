package org.eventrails.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.bytebuddy.implementation.bytecode.Throw;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.ServiceCommand;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ExceptionWrapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

@Service
public class RanchService {

	private OkHttpClient client = new OkHttpClient();

	private final ObjectMapper objectMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public String invokeDomainCommand(Ranch ranch, String commandName, String invocation) throws Throwable {
		String ranchUrl = null;
		if(ranch.getBucketType() == BucketType.LiveServer){
			ranchUrl = ranch.getArtifactCoordinates();
		}
		if(ranchUrl == null) throw new RuntimeException("Missing Ranch Deployment");

		Request request = new Request.Builder()
				.url(ranchUrl + "/er/invoke/domain-command/" + commandName)
				.post(RequestBody.create(invocation.getBytes()))
				.build();

		var resp = client.newCall(request).execute();
		if(resp.code() != 200){
			var body = Objects.requireNonNull(resp.body()).bytes();
			throw objectMapper.readValue(body, ExceptionWrapper.class).toThrowable();
		}
		return Objects.requireNonNull(resp.body()).string();
	}
}
