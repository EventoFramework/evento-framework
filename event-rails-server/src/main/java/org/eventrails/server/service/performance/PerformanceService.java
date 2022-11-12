package org.eventrails.server.service.performance;

import org.eventrails.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.performance.HandlerPerformances;
import org.eventrails.server.domain.repository.HandlerPerformancesRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class PerformanceService {

    /*

    select *,
       ifnull((last_throughput * 1000) * (250 / (250 + departures)) +
              departures / ((UNIX_TIMESTAMP(updated_at) - UNIX_TIMESTAMP(last_throughput_updated_at))) *
              (departures / (250 + departures)),
              departures / ((UNIX_TIMESTAMP(updated_at) - UNIX_TIMESTAMP(created_at))))
                                                                                                as 'c_t',
       ifnull((last_throughput * 1000) * (250 / (250 + departures)) +
              departures / ((UNIX_TIMESTAMP(updated_at) - UNIX_TIMESTAMP(last_throughput_updated_at))) *
              (departures / (250 + departures)),
              departures / ((UNIX_TIMESTAMP(updated_at) - UNIX_TIMESTAMP(created_at)))) * (mean_service_time/1000) as 'u',
       departures / ((UNIX_TIMESTAMP(updated_at) - UNIX_TIMESTAMP(last_throughput_updated_at))) as 't'
from performance__handler ph inner join core__handler h on h.uuid = ph.handler_id;
     */

    private final HandlerPerformancesRepository handlerPerformancesRepository;
    public static final double ALPHA = 0.9;
    public static final int MAX_DEPARTURES = 200;

    private final LinkedBlockingQueue<PerformanceLog> queue = new LinkedBlockingQueue<PerformanceLog>();

    private static record PerformanceLog(NodeAddress dest, Handler handler, Instant startTime){}

    private final Thread worker = new Thread(() -> {
        while (true){
            try
            {
                var log = queue.take();
                savePerformance(log.dest, log.handler, log.startTime);
            } catch (InterruptedException e)
            {
               e.printStackTrace();
            }
        }
    });

    public PerformanceService(HandlerPerformancesRepository handlerPerformancesRepository) {
        this.handlerPerformancesRepository = handlerPerformancesRepository;
        worker.start();

    }

    private void savePerformance(NodeAddress dest, Handler handler, Instant startTime){
        var now = Instant.now();
        var hp = handlerPerformancesRepository.findById(dest.getNodeId() + "_" + handler.getUuid());
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
                    dest.getNodeId() + "_" + handler.getUuid(),
                    dest.getNodeId(),
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

    public void updatePerformances(NodeAddress dest, Handler handler, Instant startTime) {
        queue.add(new PerformanceLog(dest, handler, startTime));
    }

    public void updatePerformances(NodeAddress dest, List<Handler> handlers, Instant startTime) {
        for (Handler handler : handlers) {
            updatePerformances(dest, handler, startTime);
        }
    }
}
