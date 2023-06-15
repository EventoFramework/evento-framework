package org.evento.application.proxy;

import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
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
	private final String payloadName;

	private final PerformanceService performanceService;

	private HashMap<String, AtomicInteger> invocationCounter = new HashMap<>();

	public GatewayTelemetryProxy(CommandGateway commandGateway,
								 QueryGateway queryGateway,
								 String bundle, PerformanceService performanceService,
								 String componentName, String payloadName) {
		this.commandGateway = commandGateway;
		this.queryGateway = queryGateway;
		this.bundle = bundle;
		this.performanceService = performanceService;
		this.componentName = componentName;
		this.payloadName = payloadName;
	}

	@Override
	public <R> R sendAndWait(Command command, HashMap<String, String> metadata) {
		try
		{
			return commandGateway.sendAndWait(command, metadata);
		}finally
		{
			invocationCounter.putIfAbsent(command.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(command.getClass().getSimpleName()).incrementAndGet();
		}
	}

	@Override
	public <R> R sendAndWait(Command command, HashMap<String, String> metadata, long timeout, TimeUnit unit) {
		try
		{
			return commandGateway.sendAndWait(command, metadata, timeout, unit);
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
			return commandGateway.send(command, metadata);
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
			return queryGateway.query(query, metadata);
		}finally
		{
			invocationCounter.putIfAbsent(query.getClass().getSimpleName(), new AtomicInteger());
			invocationCounter.get(query.getClass().getSimpleName()).incrementAndGet();
		}
	}

	public void sendPerformance() {
		performanceService.sendInvocations(
				bundle,
				componentName,
				payloadName,
				invocationCounter
		);

	}

	public void sendPerformance(Instant start) {
		this.performanceService.sendPerformances(
				bundle,
				componentName,
				payloadName,
				start
		);
		sendPerformance();
	}
}
