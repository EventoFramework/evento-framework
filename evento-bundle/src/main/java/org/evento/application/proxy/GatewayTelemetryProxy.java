package org.evento.application.proxy;

import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.payload.Command;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;
import org.evento.common.performance.PerformanceService;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.evento.common.performance.PerformanceService.EVENTO_TRACING_ID_METADATA;

public class GatewayTelemetryProxy implements CommandGateway, QueryGateway {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;

	private final String bundle;
	private final String componentName;
	private final String payloadName;

	private final PerformanceService performanceService;

	private final HashMap<String, AtomicInteger> invocationCounter = new HashMap<>();

	private final Message<?> handledMessage;

	public GatewayTelemetryProxy(CommandGateway commandGateway,
								 QueryGateway queryGateway,
								 String bundle, PerformanceService performanceService,
								 String componentName,
								 String payloadName,
								 Message<?> handledMessage) {
		this.commandGateway = commandGateway;
		this.queryGateway = queryGateway;
		this.bundle = bundle;
		this.performanceService = performanceService;
		this.componentName = componentName;
		this.payloadName = payloadName;
		this.handledMessage = handledMessage;
	}

	@Override
	public <R> R sendAndWait(Command command, HashMap<String, String> metadata) {
		try
		{
			return commandGateway.sendAndWait(command, manageTracing(metadata));
		}finally
		{
			invocationCounter.putIfAbsent(command.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(command.getClass().getSimpleName()).incrementAndGet();
		}
	}

	private String getCurrentTrace(){
		return handledMessage.getMetadata().getOrDefault(EVENTO_TRACING_ID_METADATA, null);
	}

	private HashMap<String, String> manageTracing(HashMap<String, String> metadata) {
		if(handledMessage.getMetadata() != null && handledMessage.getMetadata().containsKey(EVENTO_TRACING_ID_METADATA)){
			if(metadata == null )
				metadata = new HashMap<>();
			if(!metadata.containsKey(EVENTO_TRACING_ID_METADATA)){
				metadata.put(EVENTO_TRACING_ID_METADATA, getCurrentTrace());
			}
		}
		return metadata;
	}

	@Override
	public <R> R sendAndWait(Command command, HashMap<String, String> metadata, long timeout, TimeUnit unit) {
		try
		{
			return commandGateway.sendAndWait(command, manageTracing(metadata), timeout, unit);
		}finally
		{
			invocationCounter.putIfAbsent(command.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(command.getClass().getSimpleName()).incrementAndGet();
		}
	}

	@Override
	public <R> CompletableFuture<R> send(Command command, HashMap<String, String> metadata) {
		try
		{
			return commandGateway.send(command, manageTracing(metadata));
		}finally
		{
			invocationCounter.putIfAbsent(command.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(command.getClass().getSimpleName()).incrementAndGet();
		}
	}

	@Override
	public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, HashMap<String, String> metadata) {
		try
		{
			return queryGateway.query(query, manageTracing(metadata));
		}finally
		{
			invocationCounter.putIfAbsent(query.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(query.getClass().getSimpleName()).incrementAndGet();
		}
	}

	public void sendInvocationsMetric() {
		performanceService.sendInvocationsMetric(
				bundle,
				componentName,
				payloadName,
				invocationCounter,
				getCurrentTrace()
		);

	}

	public void sendServiceTimeMetric(Instant start) {
		this.performanceService.sendServiceTimeMetric(
				bundle,
				componentName,
				payloadName,
				start,
				getCurrentTrace()
		);
		sendInvocationsMetric();
	}
}
