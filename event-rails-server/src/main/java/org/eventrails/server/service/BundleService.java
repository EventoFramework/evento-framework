package org.eventrails.server.service;

import org.eventrails.parser.model.BundleDescription;
import org.eventrails.parser.model.handler.*;
import org.eventrails.parser.model.component.*;
import org.eventrails.parser.model.payload.Command;
import org.eventrails.parser.model.payload.MultipleResultQueryReturnType;
import org.eventrails.parser.model.payload.PayloadDescription;
import org.eventrails.parser.model.payload.Query;
import org.eventrails.server.domain.model.*;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.common.modeling.bundle.types.ComponentType;
import org.eventrails.common.modeling.bundle.types.HandlerType;
import org.eventrails.common.modeling.bundle.types.PayloadType;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.eventrails.server.domain.repository.PayloadRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@Service
public class BundleService {

	private final BundleRepository bundleRepository;

	private final HandlerRepository handlerRepository;
	private final PayloadRepository payloadRepository;

	public BundleService(BundleRepository bundleRepository, HandlerRepository handlerRepository, PayloadRepository payloadRepository) {
		this.bundleRepository = bundleRepository;
		this.handlerRepository = handlerRepository;
		this.payloadRepository = payloadRepository;
	}


	public void register(
			String bundleDeploymentName,
			BucketType bundleDeploymentBucketType,
			String bundleDeploymentArtifactCoordinates,
			String jarOriginalName,
			BundleDescription bundleDescription) {
		Bundle bundle = bundleRepository.save(new Bundle(
				bundleDeploymentName,
				bundleDeploymentBucketType,
				bundleDeploymentArtifactCoordinates,
				jarOriginalName,
				bundleDescription.getComponents().size()>0));
		for (PayloadDescription payloadDescription : bundleDescription.getPayloadDescriptions())
		{
			var payload = new Payload();
			payload.setName(payloadDescription.getName());
			payload.setJsonSchema(payloadDescription.getSchema().toString());
			payload.setType(PayloadType.valueOf(payloadDescription.getType()));
			payload.setUpdatedAt(Instant.now());
			payload.setRegisteredIn(bundle.getName());
			payloadRepository.save(payload);
		}
		for (Component component : bundleDescription.getComponents())
		{
			if (component instanceof Aggregate a)
			{
				for (AggregateCommandHandler aggregateCommandHandler : a.getAggregateCommandHandlers())
				{
					var handler = new Handler();
					handler.setBundle(bundle);
					handler.setComponentName(component.getComponentName());
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
					handler.setBundle(bundle);
					handler.setComponentName(component.getComponentName());
					handler.setHandlerType(HandlerType.EventSourcingHandler);
					handler.setComponentType(ComponentType.Aggregate);
					handler.setHandledPayload(payloadRepository.getById(eventSourcingHandler.getPayload().getName()));
					handler.setReturnIsMultiple(false);
					handler.setReturnType(null);
					handler.setInvocations(new HashSet<>());
					handler.generateId();
					handlerRepository.save(handler);
				}
			} else if (component instanceof Saga s)
			{
				for (SagaEventHandler sagaEventHandler : s.getSagaEventHandlers())
				{
					var handler = new Handler();
					handler.setBundle(bundle);
					handler.setComponentName(component.getComponentName());
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
			} else if (component instanceof Projection p)
			{
				for (QueryHandler queryHandler : p.getQueryHandlers())
				{
					var handler = new Handler();
					handler.setBundle(bundle);
					handler.setComponentName(component.getComponentName());
					handler.setHandlerType(HandlerType.QueryHandler);
					handler.setComponentType(ComponentType.Projection);
					handler.setHandledPayload(payloadRepository.getById(queryHandler.getPayload().getName()));
					handler.setReturnIsMultiple(queryHandler.getPayload().getReturnType() instanceof MultipleResultQueryReturnType);
					handler.setReturnType(payloadRepository.getById(queryHandler.getPayload().getReturnType().getViewName()));
					handler.setInvocations(new HashSet<>());
					handler.generateId();
					handlerRepository.save(handler);

				}
			} else if (component instanceof Projector p)
			{
				for (EventHandler eventHandler : p.getEventHandlers())
				{
					var handler = new Handler();
					handler.setBundle(bundle);
					handler.setComponentName(component.getComponentName());
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
			} else if (component instanceof org.eventrails.parser.model.component.Service s)
			{
				for (ServiceCommandHandler commandHandler : s.getCommandHandlers())
				{
					var handler = new Handler();
					handler.setBundle(bundle);
					handler.setComponentName(component.getComponentName());
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
			else if (component instanceof Invoker i)
			{
				for (InvocationHandler invocationHandler : i.getInvocationHandlers())
				{
					var handler = new Handler();
					handler.setBundle(bundle);
					handler.setComponentName(component.getComponentName());
					handler.setHandlerType(HandlerType.InvocationHandler);
					handler.setComponentType(ComponentType.Invoker);
					handler.setHandledPayload(payloadRepository.getById(invocationHandler.getPayload().getName()));
					handler.setReturnIsMultiple(false);
					handler.setReturnType(null);
					var invocations = new HashSet<Payload>();
					for (Query query : invocationHandler.getQueryInvocations())
					{
						invocations.add(payloadRepository.getById(query.getName()));
					}
					for (Command command : invocationHandler.getCommandInvocations())
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
			String bundleDeploymentName) {
		handlerRepository.deleteAllByBundle_Name(bundleDeploymentName);
		bundleRepository.deleteByName(bundleDeploymentName);
	}

	public List<Bundle> findAllBundles() {
		return bundleRepository.findAll();
	}

	public Bundle findByName(String bundleName) {
		return bundleRepository.findById(bundleName).orElseThrow();
	}
}
