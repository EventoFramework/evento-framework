package org.eventrails.server.service.performance;

import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.performance.HandlerPerformances;
import org.eventrails.server.domain.performance.modeling.Performance;
import org.eventrails.server.domain.repository.HandlerPerformancesRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PerformanceService {

    public static final String EVENT_STORE = "event-store";
    public static final String EVENT_STORE_COMPONENT = "EventStore";

    public static final String DISPATCHER = "dispatcher";
    public static final String GATEWAY_COMPONENT = "Gateway";
    public static final String SERVER = "server";

    private final HandlerPerformancesRepository handlerPerformancesRepository;
    public static final double ALPHA = 0.9;
    public static final int MAX_DEPARTURES = 200;

    private final LinkedBlockingQueue<PerformanceLog> queue = new LinkedBlockingQueue<PerformanceLog>();

    public static Instant now() {
        return Instant.now();
    }

    public Performance getPerformance(String bundle, String component, String action) {
       return handlerPerformancesRepository.findById(bundle + "_" + component + "_" + action).map(
               p -> p.toPerformance(MAX_DEPARTURES)
       ).orElse(new Performance(null, null));
    }


    private static record PerformanceLog(NodeAddress dest, Handler handler, Instant startTime){}

    public PerformanceService(HandlerPerformancesRepository handlerPerformancesRepository) {
        this.handlerPerformancesRepository = handlerPerformancesRepository;

    }

    private synchronized void savePerformance(String bundle, String component, String action, Instant startTime){
        var now = Instant.now();
        var hp = handlerPerformancesRepository.findById(bundle + "_" + component + "_" + action);
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
                    bundle + "_" + component + "_" + action,
                   bundle,
                    component,
                    action,
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

    public void updatePerformances(String bundle, String component, String action, Instant startTime) {
       savePerformance(bundle, component, action, startTime);
    }
    public void updatePerformances(String bundleId, List<String> components, String eventName, Instant startTime) {
        for (String component : components) {
            updatePerformances(bundleId, component, eventName, startTime);
        }
    }


}
