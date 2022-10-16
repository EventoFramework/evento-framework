package org.eventrails.server.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.messaging.message.DecoratedDomainCommandMessage;
import org.eventrails.modeling.messaging.message.*;
import org.eventrails.modeling.messaging.message.bus.*;
import org.eventrails.modeling.state.SerializedAggregateState;
import org.eventrails.server.es.EventStore;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.service.HandlerService;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
public class MessageGatewayService {
	private final HandlerService handlerService;

	private final LockRegistry lockRegistry;

	private final ObjectMapper objectMapper;

	private final EventStore eventStore;

	private final MessageBus messageBus;


	public MessageGatewayService(
			HandlerService handlerService,
			LockRegistry lockRegistry,
			ObjectMapper objectMapper,
			EventStore eventStore,
			MessageBus messageBus) {
		this.handlerService = handlerService;
		this.lockRegistry = lockRegistry;
		this.objectMapper = objectMapper;
		this.eventStore = eventStore;
		this.messageBus = messageBus;
		messageBus.setRequestReceiver(this::messageHandler);
	}

	private void messageHandler(Serializable request, ResponseSender response) {

		try
		{
			if (request instanceof DomainCommandMessage c)
			{

				var lock = lockRegistry.obtain("AGGREGATE:" + c.getAggregateId());
				lock.lock();
				var semaphore = new Semaphore(0);
				try
				{

					var handler = handlerService.findByPayloadName(c.getCommandName());
					var invocation = new DecoratedDomainCommandMessage();
					invocation.setCommandMessage(c);

					var snapshot = eventStore.fetchSnapshot(c.getAggregateId());
					if (snapshot == null)
					{
						invocation.setEventStream(eventStore.fetchAggregateStory(c.getAggregateId())
								.stream().map(EventStoreEntry::getEventMessage)
								.map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
						invocation.setSerializedAggregateState(new SerializedAggregateState<>(null));
					} else
					{
						invocation.setEventStream(eventStore.fetchAggregateStory(c.getAggregateId(),
										snapshot.getAggregateSequenceNumber())
								.stream().map(EventStoreEntry::getEventMessage)
								.map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
						invocation.setSerializedAggregateState(snapshot.getAggregateState());
					}
					messageBus.cast(
							messageBus.findNodeAddress(handler.getRanch().getName()),
							invocation,
							resp -> {
								eventStore.publishEvent((EventMessage<?>) resp, c.getAggregateId());
								response.sendResponse(resp);
								semaphore.release();
							},
							error -> {
								response.sendError(error.toThrowable());
								semaphore.release();
							}

					);
				} finally
				{
					semaphore.acquire();
					lock.unlock();
				}

			} else if (request instanceof ServiceCommandMessage c)
			{
				var handler = handlerService.findByPayloadName(c.getCommandName());
				messageBus.cast(
						messageBus.findNodeAddress(handler.getRanch().getName()),
						c,
						resp -> {
							if (resp != null)
								eventStore.publishEvent((EventMessage<?>) resp);
							response.sendResponse(resp);
						},
						error -> {
							response.sendError(error.toThrowable());
						}

				);

			} else if (request instanceof QueryMessage<?> q)
			{
				var handler = handlerService.findByPayloadName(q.getQueryName());
				messageBus.cast(
						messageBus.findNodeAddress(handler.getRanch().getName()),
						q,
						response::sendResponse,
						error -> {
							response.sendError(error.toThrowable());
						}
				);

			} else
			{
				throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			response.sendError(e);
		}
	}

}
