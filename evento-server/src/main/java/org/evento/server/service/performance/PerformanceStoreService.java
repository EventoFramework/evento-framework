package org.evento.server.service.performance;

import org.evento.server.domain.performance.HandlerPerformances;
import org.evento.server.domain.repository.HandlerPerformancesRepository;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PerformanceStoreService {


    public static final double ALPHA = 0.5;
    private final HandlerPerformancesRepository handlerPerformancesRepository;
    private final LockRegistry lockRegistry;
    public static Instant now() {
        return Instant.now();
    }

    public Double getMeanServiceTime(String bundle, String component, String action) {
       return handlerPerformancesRepository.findById(bundle + "_" + component + "_" + action).map(
               p -> p.getMeanServiceTime()
       ).orElse(null);
    }

    public PerformanceStoreService(HandlerPerformancesRepository handlerPerformancesRepository, LockRegistry lockRegistry) {
        this.handlerPerformancesRepository = handlerPerformancesRepository;

        this.lockRegistry = lockRegistry;
    }


    public void savePerformance(String bundle, String component, String action, long duration) {
        var pId = bundle + "_" + component + "_" + action;
        var lock = lockRegistry.obtain(pId);
        if(lock.tryLock()) {
            var hp = handlerPerformancesRepository.findById(pId);
            HandlerPerformances handlerPerformances;
            if (hp.isPresent()) {
                handlerPerformances = hp.get();
                handlerPerformances.setMeanServiceTime((((duration) * (1 - ALPHA)) + handlerPerformances.getMeanServiceTime() * ALPHA));
                handlerPerformances.setLastServiceTime(duration);
            } else {
                handlerPerformances = new HandlerPerformances(
                        pId,
                        bundle,
                        component,
                        action,
                        duration,
                        duration
                );
            }
            handlerPerformancesRepository.save(handlerPerformances);
            lock.unlock();
        }
    }



}
