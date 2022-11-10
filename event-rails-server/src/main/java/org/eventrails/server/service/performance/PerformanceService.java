package org.eventrails.server.service.performance;

import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.performance.HandlerPerformances;
import org.eventrails.server.domain.repository.HandlerPerformancesRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PerformanceService {

    private final HandlerPerformancesRepository handlerPerformancesRepository;
    public static final double ALPHA = 0.9;
    public static final int MAX_DEPARTURES = 200;

    public PerformanceService(HandlerPerformancesRepository handlerPerformancesRepository) {
        this.handlerPerformancesRepository = handlerPerformancesRepository;
    }


    public void updatePerformances(Handler handler, Instant startTime) {
        var now = Instant.now();
        var hp = handlerPerformancesRepository.findById(handler.getUuid());
        HandlerPerformances handlerPerformances;
        if(hp.isPresent()){
            handlerPerformances = hp.get();
            handlerPerformances.setMeanServiceTime((((now.toEpochMilli() - startTime.toEpochMilli())*(1-ALPHA)) + handlerPerformances.getMeanServiceTime()*ALPHA));
            handlerPerformances.setLastServiceTime(now.toEpochMilli() - startTime.toEpochMilli());
            handlerPerformances.setDepartures(handlerPerformances.getDepartures() + 1);
            if(handlerPerformances.getDepartures() == MAX_DEPARTURES){
                handlerPerformances.setLastThroughput(
                        ((double) handlerPerformances.getDepartures())/
                                (now.toEpochMilli() - (handlerPerformances.getLastThroughputUpdatedAt() == null ? handlerPerformances.getCreatedAt().toEpochMilli() : handlerPerformances.getLastThroughputUpdatedAt().toEpochMilli())));
                handlerPerformances.setLastThroughputUpdatedAt(now);
                handlerPerformances.setDepartures(0);
            }
            handlerPerformances.setUpdatedAt(now);
        }else{
            handlerPerformances = new HandlerPerformances(
                    handler.getUuid(),
                    now.toEpochMilli() - startTime.toEpochMilli(),
                    now.toEpochMilli() - startTime.toEpochMilli(),
                    null,
                    null,
                    1,
                    now,
                    now
            );
        }
        handlerPerformancesRepository.save(handlerPerformances);

    }

    public void updatePerformances(List<Handler> handlers, Instant startTime) {
        for (Handler handler : handlers) {
            updatePerformances(handler, startTime);
        }
    }
}
