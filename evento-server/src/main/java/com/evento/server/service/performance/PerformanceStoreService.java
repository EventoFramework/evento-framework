package com.evento.server.service.performance;

import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.performance.PerformanceInvocationsMessage;
import com.evento.common.performance.PerformanceService;
import com.evento.common.performance.PerformanceServiceTimeMessage;
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.domain.model.core.Handler;
import com.evento.server.domain.model.performance.HandlerInvocationCountPerformance;
import com.evento.server.domain.model.performance.HandlerServiceTimePerformance;
import com.evento.server.domain.repository.core.HandlerRepository;
import com.evento.server.domain.repository.core.PayloadRepository;
import com.evento.server.domain.repository.performance.HandlerInvocationCountPerformanceRepository;
import com.evento.server.domain.repository.performance.HandlerServiceTimePerformanceRepository;
import com.evento.server.service.performance.model.AggregatePerformancePoint;
import com.evento.server.service.performance.model.AggregationFunction;
import com.evento.server.service.performance.model.PerformancePoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.Lock;

@Service
public class PerformanceStoreService extends PerformanceService {

    public static final double ALPHA = 0.33;
    private final HandlerServiceTimePerformanceRepository handlerServiceTimePerformanceRepository;
    private final HandlerInvocationCountPerformanceRepository handlerInvocationCountPerformanceRepository;

    private final HandlerRepository handlerRepository;

    private final PayloadRepository payloadRepository;

    private final JdbcTemplate jdbcTemplate;

    private final PgDistributedLock distributedLock;

    @Value("${evento.telemetry.ttl}")
    private int ttl;

    public PerformanceStoreService(
            HandlerServiceTimePerformanceRepository handlerServiceTimePerformanceRepository,
            HandlerInvocationCountPerformanceRepository handlerInvocationCountPerformanceRepository,
            HandlerRepository handlerRepository,
            @Value("${evento.performance.capture.rate:1}") double performanceCaptureRate,
            PayloadRepository payloadRepository, JdbcTemplate jdbcTemplate,
            DataSource dataSource) {
        super(performanceCaptureRate);
        this.handlerServiceTimePerformanceRepository = handlerServiceTimePerformanceRepository;
        this.handlerInvocationCountPerformanceRepository = handlerInvocationCountPerformanceRepository;
        this.handlerRepository = handlerRepository;

        this.payloadRepository = payloadRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.distributedLock = new PgDistributedLock(dataSource);
    }

    public static Instant now() {
        return Instant.now();
    }

    public Double getMeanServiceTime(String bundle, String component, String action) {
        return handlerServiceTimePerformanceRepository.findById(bundle + "_" + component + "_" + action).map(
                HandlerServiceTimePerformance::getAgedMeanServiceTime
        ).orElse(null);
    }



    public void saveServiceTimePerformance(String bundle, String instanceId, String component, String action, long start, long end) {
        var pId = bundle + "_" + component + "_" + action;
        var duration = end - start;
        distributedLock.tryLockedArea(pId, () -> {
            var hp = handlerServiceTimePerformanceRepository.findById(pId);
            HandlerServiceTimePerformance handlerServiceTimePerformance;
            if (hp.isPresent()) {
                handlerServiceTimePerformance = hp.get();
                handlerServiceTimePerformance.setAgedMeanServiceTime((((duration) * (1 - ALPHA)) + handlerServiceTimePerformance.getAgedMeanServiceTime() * ALPHA));
                handlerServiceTimePerformance.setLastServiceTime(duration);
                handlerServiceTimePerformance.setMaxServiceTime(Math.max(handlerServiceTimePerformance.getMaxServiceTime(), duration));
                handlerServiceTimePerformance.setMinServiceTime(Math.min(handlerServiceTimePerformance.getMinServiceTime(), duration));

                if (handlerServiceTimePerformance.getLastArrival() < start) {
                    var interval = start - handlerServiceTimePerformance.getLastArrival();
                    handlerServiceTimePerformance.setAgedMeanArrivalInterval((((duration) * (1 - ALPHA)) + handlerServiceTimePerformance.getAgedMeanArrivalInterval() * ALPHA));
                    handlerServiceTimePerformance.setLastArrivalInterval(interval);
                    handlerServiceTimePerformance.setMaxArrivalInterval(Math.max(handlerServiceTimePerformance.getMaxArrivalInterval(), interval));
                    handlerServiceTimePerformance.setMinArrivalInterval(Math.min(handlerServiceTimePerformance.getMinArrivalInterval(), interval));

                    handlerServiceTimePerformance.setLastArrival(start);
                }
                handlerServiceTimePerformance.setCount(handlerServiceTimePerformance.getCount() + 1);
            } else {
                handlerServiceTimePerformance = new HandlerServiceTimePerformance(
                        pId,
                        duration,
                        duration,
                        duration,
                        duration,
                        0,
                        start,
                        0,
                        start,
                        start, 1
                );
            }
            handlerServiceTimePerformanceRepository.save(handlerServiceTimePerformance);
            jdbcTemplate.update("insert into performance__handler_service_time_ts (id, value, instance_id, timestamp) values (?,?,?,?)",
                    pId,
                    duration,
                    instanceId,
                    Instant.now().toEpochMilli());
        });
    }


    public void saveInvocationsPerformance(String bundle, String instanceId, String component, String action, HashMap<String, Integer> invocations) {
        var pId = "ic__" + bundle + "_" + component + "_" + action;
        distributedLock.tryLockedArea(pId, ()-> {
            var hId = Handler.generateId(bundle, component, action);
            handlerRepository.findById(hId).ifPresent(handler -> {
                var edited = false;
                for (String payload : invocations.keySet()) {
                    if (handler.getInvocations().values().stream().noneMatch(p -> p.getName().equals(payload))) {
                        var p = payloadRepository.findById(payload);
                        if (p.isPresent()) {
                            var line = Math.min(0, handler.getInvocations().keySet().stream().mapToInt(i -> i).min().orElse(0)) - 1;
                            handler.getInvocations().put(line, p.get());
                            edited = true;
                        }
                    }
                }
                if (edited) {
                    handler = handlerRepository.save(handler);
                }
                for (var payload : new HashSet<>(handler.getInvocations().values())) {
                    var id = bundle + "_" + component + "_" + action + '_' + payload.getName();
                    var hip = handlerInvocationCountPerformanceRepository.findById(id).orElseGet(()
                            -> {
                        var hi = new HandlerInvocationCountPerformance();
                        hi.setId(id);
                        hi.setLastCount(0);
                        hi.setMeanProbability(0);
                        return handlerInvocationCountPerformanceRepository.save(hi);
                    });
                    hip.setLastCount(invocations.getOrDefault(payload.getName(), 0));
                    hip.setMeanProbability(((1 - ALPHA) * hip.getMeanProbability()) +
                            (ALPHA * invocations.getOrDefault(payload.getName(), 0)));
                    handlerInvocationCountPerformanceRepository.save(hip);
                    jdbcTemplate.update("insert into performance__handler_invocation_count_ts (id, instance_id, timestamp) values (?, ?, ?)",
                            id, instanceId, Instant.now().toEpochMilli());
                }
            });
        });
    }


    public void sendAggregateServiceTimeMetric(String bundle, String instance,
                                               String sourceBundle,
                                               String sourceInstance,
                                               long eventSequenceNumber,
                                               String component,
                                               String commandName, Instant start,
                                               Instant lockAcquired, Instant retrieveDone,
                                               Instant computationDone, Instant published,
                                               String aggregateId, boolean force) {
        if (!force)
            if (super.random.nextDouble(0.0, 1.0) > super.performanceRate) return;

        executor.execute(() -> {
            try {
                jdbcTemplate.update(
                        "insert into performance__aggregate_handler_invocation_count_ts " +
                                "values (?,?,?,?,?,?,?,?,?,?,?,?)",
                        bundle + "_" + component + "_" + commandName,
                        aggregateId,
                        eventSequenceNumber,
                        instance,
                        sourceBundle,
                        sourceInstance,
                        start.toEpochMilli(),
                        published.toEpochMilli() - start.toEpochMilli(),
                        lockAcquired.toEpochMilli() - start.toEpochMilli(),
                        retrieveDone.toEpochMilli() - lockAcquired.toEpochMilli(),
                        computationDone.toEpochMilli() - retrieveDone.toEpochMilli(),
                        published.toEpochMilli() - computationDone.toEpochMilli()
                )
                ;
            } catch (Exception e) {
                logger.error("Error during aggregatePerformance Save", e);
            }
        });


    }


    public Double getInvocationProbability(String bundle, String component, String action, String payload) {
        return handlerInvocationCountPerformanceRepository.findById(bundle + "_" + component + "_" + action + "_" + payload).map(
                HandlerInvocationCountPerformance::getMeanProbability
        ).orElse(1.0);
    }

    @Override
    public void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) {
        saveServiceTimePerformance(
                message.getBundle(),
                message.getInstanceId(),
                message.getComponent(),
                message.getAction(),
                message.getStart(),
                message.getEnd()
        );
    }

    @Override
    public void sendInvocationMetricMessage(PerformanceInvocationsMessage message) {
        saveInvocationsPerformance(message.getBundle(),
                message.getInstanceId(),
                message.getComponent(),
                message.getAction(),
                message.getInvocations());
    }

    public Map<String, Collection<PerformancePoint>> getComponentPerformance(String bundleId,
                                                                             String componentId,
                                                                             AggregationFunction serviceTimeAggregationFunction,
                                                                             @RequestParam(defaultValue = "60") Integer interval,
                                                                             ZonedDateTime from,
                                                                             ZonedDateTime to) {

        var sql = "SELECT to_timestamp(gs.interval_start / 1000) as ts, " +
                "       COALESCE(COUNT(performance__handler_service_time_ts.id), 0) AS count, " +
                "      " + serviceTimeAggregationFunction + "(performance__handler_service_time_ts.value) AS value " +
                "FROM generate_series( " +
                "             ?::bigint, " +
                "             ?::bigint, " +
                "             ? " +
                "     ) AS gs(interval_start) " +
                "         LEFT JOIN performance__handler_service_time_ts " +
                "                   ON performance__handler_service_time_ts.timestamp >= gs.interval_start " +
                "                       AND performance__handler_service_time_ts.timestamp < gs.interval_start + " +
                "                                                                            ? " +
                "AND performance__handler_service_time_ts.id = ? " +
                "GROUP BY gs.interval_start " +
                "ORDER BY gs.interval_start";
        var resp = new HashMap<String, Collection<PerformancePoint>>();
        var handlers = handlerRepository.findAllHandledPayloadsNameByComponentName(componentId);

        var tsFrom = from.toInstant().toEpochMilli();
        var toTs = to.toInstant().toEpochMilli();

        for (String handler : handlers) {
            var ts_id = bundleId + "_" + componentId + "_" + handler;
            var i = interval * 1000;
            resp.put(handler, jdbcTemplate.query(sql, new Object[]{tsFrom, toTs, i, i, ts_id}, (rs, rowNum) -> {
                var pp = new PerformancePoint();
                pp.setTimestamp(rs.getString("ts"));
                pp.setCount(rs.getBigDecimal("count"));
                pp.setServiceTime(rs.getBigDecimal("value"));
                return pp;
            }));
        }
        return resp;
    }

    public Map<String, Collection<AggregatePerformancePoint>> getAggregatePerformance(String bundleId,
                                                                                      String componentId,
                                                                                      AggregationFunction serviceTimeAggregationFunction,
                                                                                      @RequestParam(defaultValue = "60") Integer interval,
                                                                                      ZonedDateTime from,
                                                                                      ZonedDateTime to) {

        var sql = "SELECT to_timestamp(gs.interval_start / 1000) as ts, " +
                "       COALESCE(COUNT(handler.id), 0) AS count, " +
                "      " + serviceTimeAggregationFunction + "(handler.compute) AS compute, " +
                "      " + serviceTimeAggregationFunction + "(handler.publish) AS publish, " +
                "      " + serviceTimeAggregationFunction + "(handler.retrieve) AS retrieve, " +
                "      " + serviceTimeAggregationFunction + "(handler.lock) AS lock " +
                "FROM generate_series( " +
                "             ?::bigint, " +
                "             ?::bigint, " +
                "             ? " +
                "     ) AS gs(interval_start) " +
                "         LEFT JOIN performance__aggregate_handler_invocation_count_ts handler " +
                "                   ON handler.timestamp >= gs.interval_start " +
                "                       AND handler.timestamp < gs.interval_start + " +
                "                                               ? " +
                "AND handler.id = ? " +
                "GROUP BY gs.interval_start " +
                "ORDER BY gs.interval_start;";
        var resp = new HashMap<String, Collection<AggregatePerformancePoint>>();
        var handlers = handlerRepository.findAllByComponentComponentName(componentId);

        var tsFrom = from.toInstant().toEpochMilli();
        var toTs = to.toInstant().toEpochMilli();

        for (var handler : handlers) {
            if (handler.getHandlerType() != HandlerType.AggregateCommandHandler) continue;
            var id = bundleId + "_" + componentId + "_" + handler.getHandledPayload().getName();
            var i = interval * 1000;
            resp.put(handler.getHandledPayload().getName(), jdbcTemplate.query(sql, new Object[]{tsFrom, toTs, i, i, id
            }, (rs, rowNum) -> {
                var pp = new AggregatePerformancePoint();
                pp.setTimestamp(rs.getString("ts"));
                pp.setCount(rs.getBigDecimal("count"));
                pp.setServiceTime(rs.getBigDecimal("compute"));
                pp.setStore(rs.getBigDecimal("publish"));
                pp.setRetrieve(rs.getBigDecimal("retrieve"));
                pp.setLock(rs.getBigDecimal("lock"));
                return pp;
            }));
        }
        return resp;
    }


    @Scheduled(cron = "0 0 * * * *")
    public void cleanupTelemetry() {
        var delta = Instant.now().toEpochMilli() - (ttl * 24L * 60L * 60L * 1000L);
        jdbcTemplate.update("delete from performance__handler_service_time_ts where timestamp < ?", delta);
        jdbcTemplate.update("delete from performance__handler_invocation_count_ts where timestamp < ?", delta);
        jdbcTemplate.update("delete from performance__aggregate_handler_invocation_count_ts where timestamp < ?", delta);
    }

}
