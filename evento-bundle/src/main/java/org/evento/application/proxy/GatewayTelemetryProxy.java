package org.evento.application.proxy;

import org.evento.application.performance.TracingAgent;
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


public class GatewayTelemetryProxy implements CommandGateway, QueryGateway {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;

	private final String bundle;
	private final String componentName;

	private final PerformanceService performanceService;

	private final HashMap<String, AtomicInteger> invocationCounter = new HashMap<>();

	private final Message<?> handledMessage;
	private final TracingAgent tracingAgent;

	public GatewayTelemetryProxy(CommandGateway commandGateway,
								 QueryGateway queryGateway,
								 String bundle, PerformanceService performanceService,
								 String componentName,
								 Message<?> handledMessage,
								 TracingAgent tracingAgent) {
		this.commandGateway = commandGateway;
		this.queryGateway = queryGateway;
		this.bundle = bundle;
		this.performanceService = performanceService;
		this.componentName = componentName;
		this.handledMessage = handledMessage;
		this.tracingAgent = tracingAgent;
	}

	@Override
	public <R> R sendAndWait(Command command, HashMap<String, String> metadata, Message<?> handledMessage) {
		try
		{
			metadata = tracingAgent.correlate(metadata, this.handledMessage);
			return commandGateway.sendAndWait(command, metadata, this.handledMessage);
		} finally
		{
			invocationCounter.putIfAbsent(command.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(command.getClass().getSimpleName()).incrementAndGet();
		}
	}


	@Override
	public <R> R sendAndWait(Command command, HashMap<String, String> metadata, Message<?> handledMessage, long timeout, TimeUnit unit) {
		try
		{
			metadata = tracingAgent.correlate(metadata, this.handledMessage);
			return commandGateway.sendAndWait(command, metadata, this.handledMessage, timeout, unit);
		} finally
		{
			invocationCounter.putIfAbsent(command.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(command.getClass().getSimpleName()).incrementAndGet();
		}
	}

	@Override
	public <R> CompletableFuture<R> send(Command command, HashMap<String, String> metadata, Message<?> handledMessage) {
		try
		{
			metadata = tracingAgent.correlate(metadata, this.handledMessage);
			return commandGateway.send(command, metadata, this.handledMessage);
		} finally
		{
			invocationCounter.putIfAbsent(command.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(command.getClass().getSimpleName()).incrementAndGet();
		}
	}

	@Override
	public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, HashMap<String, String> metadata, Message<?> handledMessage) {
		try
		{
			metadata = tracingAgent.correlate(metadata, this.handledMessage);
			return queryGateway.query(query, metadata, this.handledMessage);
		} finally
		{
			invocationCounter.putIfAbsent(query.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(query.getClass().getSimpleName()).incrementAndGet();
		}
	}

	public void sendInvocationsMetric() {
		performanceService.sendInvocationsMetric(
				bundle,
				componentName,
				handledMessage,
				invocationCounter
		);

	}

	public void sendServiceTimeMetric(Instant start) {
		this.performanceService.sendServiceTimeMetric(
				bundle,
				componentName,
				handledMessage,
				start
		);
		sendInvocationsMetric();
	}
}
