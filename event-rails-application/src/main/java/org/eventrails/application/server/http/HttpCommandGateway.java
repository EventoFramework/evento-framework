package org.eventrails.application.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eventrails.application.utils.ClusterUrls;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;
import org.eventrails.modeling.messaging.payload.Command;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.ServiceCommand;
import org.eventrails.shared.ObjectMapperUtils;
import org.eventrails.shared.exceptions.ThrowableWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressWarnings("unchecked")
public class HttpCommandGateway implements CommandGateway {

	private final ClusterUrls clusterUrls;
	private OkHttpClient client = new OkHttpClient();
	private ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public HttpCommandGateway(String serverUrls) {
		this.clusterUrls = new ClusterUrls(serverUrls);
	}

	@Override
	public <R> CompletableFuture<R> send(Command command) {
		CompletableFuture<R> completableFuture = new CompletableFuture<>();

		try
		{
			Request request = new Request.Builder()
					.url(clusterUrls.pickClusterUrl() + "/handle")
					.post(RequestBody.create(payloadMapper.writeValueAsString(command instanceof DomainCommand ? new DomainCommandMessage((DomainCommand) command) : new ServiceCommandMessage((ServiceCommand) command)).getBytes()))
					.build();
			client.newCall(request).enqueue(new Callback() {
				@Override
				public void onFailure(@NotNull Call call, @NotNull IOException e) {
					completableFuture.completeExceptionally(e);
				}

				@Override
				public void onResponse(@NotNull Call call, @NotNull Response response){
					try
					{
						var body = Objects.requireNonNull(response.body()).bytes();
						if (response.code() == 200)
							completableFuture.complete((R) payloadMapper.readValue(body, Object.class));
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
		return completableFuture.thenApply(m -> m);
	}

	@Override
	public <R> R sendAndWait(Command command){
		try
		{
			return (R) send(command).get();
		} catch (InterruptedException | ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public <R> R sendAndWait(Command command, long timeout, TimeUnit unit) {
		try
		{
			return (R) send(command).get(timeout, unit);
		} catch (ExecutionException | InterruptedException | TimeoutException e)
		{
			throw new RuntimeException(e);
		}
	}
}