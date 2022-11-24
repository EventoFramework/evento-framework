package org.eventrails.server.service.performance;

import org.eventrails.server.domain.performance.HandlerPerformances;
import org.eventrails.server.domain.performance.modeling.Performance;
import org.eventrails.server.domain.repository.HandlerPerformancesRepository;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PerformanceStoreService {


    public static final double ALPHA = 0.9;
    public static final int MAX_DEPARTURES = 200;
    private final HandlerPerformancesRepository handlerPerformancesRepository;
    private final LockRegistry lockRegistry;
    public static Instant now() {
        return Instant.now();
    }

    public Performance getPerformance(String bundle, String component, String action) {
       return handlerPerformancesRepository.findById(bundle + "_" + component + "_" + action).map(
               p -> p.toPerformance(MAX_DEPARTURES)
       ).orElse(new Performance(null, null));
    }




    public PerformanceStoreService(HandlerPerformancesRepository handlerPerformancesRepository, LockRegistry lockRegistry) {
        this.handlerPerformancesRepository = handlerPerformancesRepository;

        this.lockRegistry = lockRegistry;
    }


    public void savePerformance(String bundle, String component, String action, long duration) {
        var pId = bundle + "_" + component + "_" + action;
        var lock = lockRegistry.obtain(pId);
        if(lock.tryLock()) {
            var now = Instant.now();
            var hp = handlerPerformancesRepository.findById(pId);
            HandlerPerformances handlerPerformances;
            if (hp.isPresent()) {
                handlerPerformances = hp.get();
                handlerPerformances.setMeanServiceTime((((duration) * (1 - ALPHA)) + handlerPerformances.getMeanServiceTime() * ALPHA));
                handlerPerformances.setLastServiceTime(duration);
                handlerPerformances.setDepartures(handlerPerformances.getDepartures() + 1);
                if (handlerPerformances.getDepartures() == MAX_DEPARTURES) {
                    handlerPerformances.setLastThroughput(
                            ((double) handlerPerformances.getDepartures()) /
                                    (now.toEpochMilli() - (handlerPerformances.getLastThroughputUpdatedAt() == null ? handlerPerformances.getCreatedAt().toEpochMilli() : handlerPerformances.getLastThroughputUpdatedAt().toEpochMilli())));
                    handlerPerformances.setLastThroughputUpdatedAt(now);
                    handlerPerformances.setDepartures(0);
                }
                handlerPerformances.setUpdatedAt(now);
            } else {
                handlerPerformances = new HandlerPerformances(
                        pId,
                        bundle,
                        component,
                        action,
                        duration,
                        duration,
                        null,
                        null,
                        1,
                        now,
                        now
                );
            }
            handlerPerformancesRepository.save(handlerPerformances);
            lock.unlock();
        }
    }



}
