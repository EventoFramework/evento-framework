package org.eventrails.application.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eventrails.application.utils.ClusterUrls;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.message.QueryMessage;
import org.eventrails.modeling.messaging.payload.Query;
import org.eventrails.modeling.messaging.query.QueryResponse;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class HttpQueryGateway implements QueryGateway {

	private final ClusterUrls clusterUrls;
	private OkHttpClient client = new OkHttpClient();
	private ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public HttpQueryGateway(String serverUrls) {
		this.clusterUrls = new ClusterUrls(serverUrls);
	}

	@Override
	public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query) {
		CompletableFuture<T> completableFuture = new CompletableFuture<>();
		try
		{
			Request request = new Request.Builder()
					.url(clusterUrls.pickClusterUrl() + "/handle")
					.post(RequestBody.create(payloadMapper.writeValueAsString(new QueryMessage<>(query)).getBytes()))
					.build();
			client.newCall(request).enqueue(new Callback() {
				@Override
				public void onFailure(@NotNull Call call, @NotNull IOException e) {
					completableFuture.completeExceptionally(e);
				}

				@Override
				public void onResponse(@NotNull Call call, @NotNull Response response) {
					try
					{
						var body = Objects.requireNonNull(response.body()).bytes();
						if (response.code() == 200)
							completableFuture.complete(payloadMapper.readValue(body, query.getResponseType()));
						else
							completableFuture.completeExceptionally(payloadMapper.readValue(body, ThrowableWrapper.class).toThrowable());
					}catch (Exception e){
						completableFuture.completeExceptionally(e);
					}
				}
			});
		} catch (Exception e)
		{
			completableFuture.completeExceptionally(e);
		}
		return completableFuture;
	}
}
