package org.evento.server.service.performance;

import org.evento.server.domain.model.Handler;
import org.evento.server.domain.model.Payload;
import org.evento.server.domain.performance.HandlerInvocationCountPerformance;
import org.evento.server.domain.performance.HandlerServiceTimePerformance;
import org.evento.server.domain.performance.queue.Node;
import org.evento.server.domain.repository.ComponentRepository;
import org.evento.server.domain.repository.HandlerInvocationCountPerformanceRepository;
import org.evento.server.domain.repository.HandlerRepository;
import org.evento.server.domain.repository.HandlerServiceTimePerformanceRepository;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class PerformanceStoreService {

	public static final double ALPHA = 0.33;
	private final HandlerServiceTimePerformanceRepository handlerServiceTimePerformanceRepository;
	private final HandlerInvocationCountPerformanceRepository handlerInvocationCountPerformanceRepository;

	private final HandlerRepository handlerRepository;

	private final ComponentRepository componentRepository;
	private final LockRegistry lockRegistry;

	public static Instant now() {
		return Instant.now();
	}

	public Double getMeanServiceTime(String bundle, String component, String action) {
		return handlerServiceTimePerformanceRepository.findById(bundle + "_" + component + "_" + action).map(
				HandlerServiceTimePerformance::getMeanServiceTime
		).orElse(null);
	}

	public PerformanceStoreService(HandlerServiceTimePerformanceRepository handlerServiceTimePerformanceRepository, HandlerInvocationCountPerformanceRepository handlerInvocationCountPerformanceRepository, HandlerRepository handlerRepository, ComponentRepository componentRepository, LockRegistry lockRegistry) {
		this.handlerServiceTimePerformanceRepository = handlerServiceTimePerformanceRepository;
		this.handlerInvocationCountPerformanceRepository = handlerInvocationCountPerformanceRepository;
		this.handlerRepository = handlerRepository;
		this.componentRepository = componentRepository;

		this.lockRegistry = lockRegistry;
	}


	public void saveServiceTimePerformance(String bundle, String component, String action, long duration) {
		var pId =  bundle + "_" + component + "_" + action;
		var lock = lockRegistry.obtain(pId);
		if (lock.tryLock())
		{
			try
			{
				var hp = handlerServiceTimePerformanceRepository.findById(pId);
				HandlerServiceTimePerformance handlerServiceTimePerformance;
				if (hp.isPresent())
				{
					handlerServiceTimePerformance = hp.get();
					handlerServiceTimePerformance.setMeanServiceTime((((duration) * (1 - ALPHA)) + handlerServiceTimePerformance.getMeanServiceTime() * ALPHA));
					handlerServiceTimePerformance.setLastServiceTime(duration);
				} else
				{
					handlerServiceTimePerformance = new HandlerServiceTimePerformance(
							pId,
							duration,
							duration
					);
				}
				handlerServiceTimePerformanceRepository.save(handlerServiceTimePerformance);
			}finally
			{
				lock.unlock();
			}
		}
	}


	public void saveInvocationsPerformance(String bundle, String component, String action, HashMap<String, Integer> invocations) throws NoSuchAlgorithmException {
		var pId = "ic__" + bundle + "_" + component + "_" + action;
		var lock = lockRegistry.obtain(pId);
		if (lock.tryLock())
		{
			try
			{
				var hId = Handler.generateId(bundle, component, action);
				var handler = handlerRepository.findById(hId).orElseThrow();
				for (String payload : handler.getInvocations().values().stream().map(Payload::getName).collect(Collectors.toSet()))
				{
					var id = bundle + "_" + component + "_" + action + '_' + payload;
					var hip = handlerInvocationCountPerformanceRepository.findById(id).orElseGet(()
					-> {
						var hi = new HandlerInvocationCountPerformance();
						hi.setId(id);
						hi.setLastCount(0);
						hi.setMeanProbability(0);
						return handlerInvocationCountPerformanceRepository.save(hi);
					});
					hip.setLastCount(invocations.getOrDefault(payload, 0));
					hip.setMeanProbability(((1 - ALPHA) * hip.getMeanProbability()) + (ALPHA * invocations.getOrDefault(payload, 0)));
					handlerInvocationCountPerformanceRepository.save(hip);
				}

			}finally
			{
				lock.unlock();
			}
		}
	}


	public Double getInvocationProbability(String bundle, String component, String action, String payload) {
		return handlerInvocationCountPerformanceRepository.findById(bundle + "_" + component + "_" + action+ "_" + payload).map(
				HandlerInvocationCountPerformance::getMeanProbability
		).orElse(1.0);
	}
}
