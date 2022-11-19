package org.eventrails.server.domain.performance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eventrails.server.domain.performance.modeling.Performance;
import org.eventrails.server.service.performance.Action;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "performance__handler")
public class HandlerPerformances {

    @Id
    private String id;

    private String bundle;

    private String component;

    private String action;

    private double lastServiceTime;

    private double meanServiceTime;

    private Double lastThroughput;

    private Instant lastThroughputUpdatedAt;

    private int departures;

    private Instant updatedAt;

    private Instant createdAt;

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
    public double calcMeanThroughput() {
        return 0;
    }

    public Performance toPerformance(double maxDepartures ) {
        var t = ((double) departures) / ((double)(updatedAt.toEpochMilli() - createdAt.toEpochMilli()));
        if(lastThroughput != null){
            t = lastThroughput * (maxDepartures / (maxDepartures + departures))  + t * (departures / (maxDepartures + departures));
        }
        return new Performance(meanServiceTime/1000.0, t*1000.0);
    }
}
