package org.eventrails.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class RanchService {

	private OkHttpClient client = new OkHttpClient();

	private final ObjectMapper objectMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public CompletableFuture<String> invokeDomainCommand(Ranch ranch, String commandName, String invocation) throws Throwable {
		String ranchUrl = fetchRanchUrl(ranch);

		Request request = new Request.Builder()
				.url(ranchUrl + "/er/invoke/domain-command/" + commandName)
				.post(RequestBody.create(invocation.getBytes()))
				.build();

		return getStringCompletableFuture(request);

	}

	public CompletableFuture<String> invokeQuery(Ranch ranch, String queryName, String invocation) {
		String ranchUrl = fetchRanchUrl(ranch);


		Request request = new Request.Builder()
				.url(ranchUrl + "/er/invoke/query/" + queryName)
				.post(RequestBody.create(invocation.getBytes()))
				.build();

		return getStringCompletableFuture(request);
	}

	public CompletableFuture<String> invokeServiceCommand(Ranch ranch, String commandName, String invocation) {
		String ranchUrl = fetchRanchUrl(ranch);

		Request request = new Request.Builder()
				.url(ranchUrl + "/er/invoke/service-command/" + commandName)
				.post(RequestBody.create(invocation.getBytes()))
				.build();

		return getStringCompletableFuture(request);
	}

	@NotNull
	private CompletableFuture<String> getStringCompletableFuture(Request request) {
		CompletableFuture<String> resp = new CompletableFuture<>();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(@NotNull Call call, @NotNull IOException e) {
				resp.completeExceptionally(e);
			}

			@Override
			public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
				if(response.code() != 200){
					var body = Objects.requireNonNull(response.body()).bytes();
					resp.completeExceptionally(objectMapper.readValue(body, ThrowableWrapper.class).toThrowable());
				}
				resp.complete(Objects.requireNonNull(response.body()).string());
			}
		});

		return resp;
	}

	private String fetchRanchUrl(Ranch ranch) {
		if(ranch.getBucketType() == BucketType.LiveServer){
			return ranch.getArtifactCoordinates();
		}
		throw new RuntimeException("Missing Ranch Deployment");
	}


}
