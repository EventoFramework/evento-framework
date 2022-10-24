package org.eventrails.server.service;

import org.eventrails.parser.model.RanchApplicationDescription;
import org.eventrails.parser.model.handler.*;
import org.eventrails.parser.model.node.*;
import org.eventrails.parser.model.payload.Command;
import org.eventrails.parser.model.payload.MultipleResultQueryReturnType;
import org.eventrails.parser.model.payload.PayloadDescription;
import org.eventrails.parser.model.payload.Query;
import org.eventrails.server.domain.model.*;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.types.ComponentType;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.model.types.PayloadType;
import org.eventrails.server.domain.repository.RanchRepository;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.eventrails.server.domain.repository.PayloadRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;

@Service
public class RanchApplicationService {

	private final RanchRepository ranchRepository;

	private final HandlerRepository handlerRepository;
	private final PayloadRepository payloadRepository;

	public RanchApplicationService(RanchRepository ranchRepository, HandlerRepository handlerRepository, PayloadRepository payloadRepository) {
		this.ranchRepository = ranchRepository;
		this.handlerRepository = handlerRepository;
		this.payloadRepository = payloadRepository;
	}


	public void register(
			String ranchDeploymentName,
			BucketType ranchDeploymentBucketType,
			String ranchDeploymentArtifactCoordinates,
			RanchApplicationDescription ranchApplicationDescription) {
		Ranch ranch = new Ranch();
		ranch.setName(ranchDeploymentName);
		ranch.setBucketType(ranchDeploymentBucketType);
		ranch.setArtifactCoordinates(ranchDeploymentArtifactCoordinates);
		ranch = ranchRepository.save(ranch);
		for (PayloadDescription payloadDescription : ranchApplicationDescription.getPayloadDescriptions())
		{
			var payload = new Payload();
			payload.setName(payloadDescription.getName());
			payload.setJsonSchema(payloadDescription.getSchema().toString());
			payload.setType(PayloadType.valueOf(payloadDescription.getType()));
			payloadRepository.save(payload);
		}
		for (Node node : ranchApplicationDescription.getNodes())
		{
			if (node instanceof Aggregate a)
			{
				for (AggregateCommandHandler aggregateCommandHandler : a.getAggregateCommandHandlers())
				{
					var handler = new Handler();
					handler.setRanch(ranch);
					handler.setComponentName(node.getComponentName());
					handler.setHandlerType(HandlerType.AggregateCommandHandler);
					handler.setComponentType(ComponentType.Aggregate);
					handler.setHandledPayload(payloadRepository.getById(aggregateCommandHandler.getPayload().getName()));
					handler.setReturnIsMultiple(false);
					handler.setReturnType(payloadRepository.getById(aggregateCommandHandler.getProducedEvent().getName()));
					handler.setInvocations(new HashSet<>());
					handler.generateId();
					handlerRepository.save(handler);
				}
				for (EventSourcingHandler eventSourcingHandler : a.getEventSourcingHandlers())
				{
					var handler = new Handler();
					handler.setRanch(ranch);
					handler.setComponentName(node.getComponentName());
					handler.setHandlerType(HandlerType.EventSourcingHandler);
					handler.setComponentType(ComponentType.Aggregate);
					handler.setHandledPayload(payloadRepository.getById(eventSourcingHandler.getPayload().getName()));
					handler.setReturnIsMultiple(false);
					handler.setReturnType(null);
					handler.setInvocations(new HashSet<>());
					handler.generateId();
					handlerRepository.save(handler);
				}
			} else if (node instanceof Saga s)
			{
				for (SagaEventHandler sagaEventHandler : s.getSagaEventHandlers())
				{
					var handler = new Handler();
					handler.setRanch(ranch);
					handler.setComponentName(node.getComponentName());
					handler.setHandlerType(HandlerType.SagaEventHandler);
					handler.setComponentType(ComponentType.Saga);
					handler.setHandledPayload(payloadRepository.getById(sagaEventHandler.getPayload().getName()));
					handler.setReturnIsMultiple(false);
					handler.setReturnType(null);
					handler.setAssociationProperty(sagaEventHandler.getAssociationProperty());
					var invocations = new HashSet<Payload>();
					for (Command command : sagaEventHandler.getCommandInvocations())
					{
						invocations.add((payloadRepository.getById(command.getName())));
					}
					for (Query query : sagaEventHandler.getQueryInvocations())
					{
						invocations.add((payloadRepository.getById(query.getName())));
					}
					handler.setInvocations(invocations);
					handler.generateId();
					handlerRepository.save(handler);
				}
			} else if (node instanceof Projection p)
			{
				for (QueryHandler queryHandler : p.getQueryHandlers())
				{
					var handler = new Handler();
					handler.setRanch(ranch);
					handler.setComponentName(node.getComponentName());
					handler.setHandlerType(HandlerType.QueryHandler);
					handler.setComponentType(ComponentType.Projection);
					handler.setHandledPayload(payloadRepository.getById(queryHandler.getPayload().getName()));
					handler.setReturnIsMultiple(queryHandler.getPayload().getReturnType() instanceof MultipleResultQueryReturnType);
					handler.setReturnType(payloadRepository.getById(queryHandler.getPayload().getReturnType().getViewName()));
					handler.setInvocations(new HashSet<>());
					handler.generateId();
					handlerRepository.save(handler);

				}
			} else if (node instanceof Projector p)
			{
				for (EventHandler eventHandler : p.getEventHandlers())
				{
					var handler = new Handler();
					handler.setRanch(ranch);
					handler.setComponentName(node.getComponentName());
					handler.setHandlerType(HandlerType.EventHandler);
					handler.setComponentType(ComponentType.Projector);
					handler.setHandledPayload(payloadRepository.getById(eventHandler.getPayload().getName()));
					handler.setReturnIsMultiple(false);
					handler.setReturnType(null);
					var invocations = new HashSet<Payload>();
					for (Query query : eventHandler.getQueryInvocations())
					{
						invocations.add((payloadRepository.getById(query.getName())));
					}
					handler.setInvocations(invocations);
					handler.generateId();
					handlerRepository.save(handler);

				}
			} else if (node instanceof org.eventrails.parser.model.node.Service s)
			{
				for (ServiceCommandHandler commandHandler : s.getCommandHandlers())
				{
					var handler = new Handler();
					handler.setRanch(ranch);
					handler.setComponentName(node.getComponentName());
					handler.setHandlerType(HandlerType.CommandHandler);
					handler.setComponentType(ComponentType.Service);
					handler.setHandledPayload(payloadRepository.getById(commandHandler.getPayload().getName()));
					handler.setReturnIsMultiple(false);
					handler.setReturnType(commandHandler.getProducedEvent() == null ? null : payloadRepository.getById(commandHandler.getProducedEvent().getName()));
					var invocations = new HashSet<Payload>();
					for (Query query : commandHandler.getQueryInvocations())
					{
						invocations.add(payloadRepository.getById(query.getName()));
					}
					for (Command command : commandHandler.getCommandInvocations())
					{
						invocations.add(payloadRepository.getById(command.getName()));
					}
					handler.setInvocations(invocations);
					handler.generateId();
					handlerRepository.save(handler);
				}
			}
		}
	}

	@Transactional
	public void unregister(
			String ranchDeploymentName) {
		handlerRepository.deleteAllByRanch_Name(ranchDeploymentName);
		ranchRepository.deleteByName(ranchDeploymentName);
	}

	public List<Ranch> findAllRanches() {
		return ranchRepository.findAll();
	}
}
