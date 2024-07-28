package com.evento.server.service.performance;

import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.performance.PerformanceInvocationsMessage;
import com.evento.common.performance.PerformanceService;
import com.evento.common.performance.PerformanceServiceTimeMessage;
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
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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

    private final LockRegistry lockRegistry;
    private final PayloadRepository payloadRepository;

    private final JdbcTemplate jdbcTemplate;

    @Value("${evento.telemetry.ttl}")
    private int ttl;

    public PerformanceStoreService(
            HandlerServiceTimePerformanceRepository handlerServiceTimePerformanceRepository,
            HandlerInvocationCountPerformanceRepository handlerInvocationCountPerformanceRepository,
            HandlerRepository handlerRepository, LockRegistry lockRegistry,
            @Value("${evento.performance.capture.rate:1}") double performanceCaptureRate,
            PayloadRepository payloadRepository, JdbcTemplate jdbcTemplate) {
        super(performanceCaptureRate);
        this.handlerServiceTimePerformanceRepository = handlerServiceTimePerformanceRepository;
        this.handlerInvocationCountPerformanceRepository = handlerInvocationCountPerformanceRepository;
        this.handlerRepository = handlerRepository;

        this.lockRegistry = lockRegistry;
        this.payloadRepository = payloadRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static Instant now() {
        return Instant.now();
    }

    public Double getMeanServiceTime(String bundle, String component, String action) {
        return handlerServiceTimePerformanceRepository.findById(bundle + "_" + component + "_" + action).map(
                HandlerServiceTimePerformance::getAgedMeanServiceTime
        ).orElse(null);
    }

    private static boolean tryLock(Lock lock) {
        try {
            return lock.tryLock();
        } catch (Exception e) {
            return false;
        }
    }

    public void saveServiceTimePerformance(String bundle, String instanceId, String component, String action, long start, long end) {
        var pId = bundle + "_" + component + "_" + action;
        var lock = lockRegistry.obtain(pId);
        var duration = end - start;
        if (tryLock(lock)) {
            try {
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
            } finally {
                lock.unlock();
            }
        }
    }


    public void saveInvocationsPerformance(String bundle, String instanceId, String component, String action, HashMap<String, Integer> invocations) {
        var pId = "ic__" + bundle + "_" + component + "_" + action;
        var lock = lockRegistry.obtain(pId);
        if (tryLock(lock)) {
            try {
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
            } finally {
                lock.unlock();
            }
        }
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
                "      " + serviceTimeAggregationFunction + "(handler.value) AS serviceTime, " +
                "      " + serviceTimeAggregationFunction + "(eventStore.value) AS store, " +
                "      " + serviceTimeAggregationFunction + "(server.value) AS retrieve, " +
                "      " + serviceTimeAggregationFunction + "(lock.value) AS lock " +
                "FROM generate_series( " +
                "             ?::bigint, " +
                "             ?::bigint, " +
                "             ? " +
                "     ) AS gs(interval_start) " +
                "         LEFT JOIN performance__handler_service_time_ts handler " +
                "                   ON handler.timestamp >= gs.interval_start " +
                "                       AND handler.timestamp < gs.interval_start + " +
                "                                               ? " +
                "AND handler.id = ? " +
                "         LEFT JOIN performance__handler_service_time_ts eventStore " +
                "                   ON eventStore.timestamp >= gs.interval_start " +
                "                       AND eventStore.timestamp < gs.interval_start + " +
                "                                                  ? " +
                "                       AND eventStore.id = ? " +
                " " +
                "         LEFT JOIN performance__handler_service_time_ts server " +
                "                   ON server.timestamp >= gs.interval_start " +
                "                       AND server.timestamp < gs.interval_start + " +
                "                                                  ?" +
                "                       AND server.id = ? " +
                "         LEFT JOIN performance__handler_service_time_ts lock " +
                "                   ON lock.timestamp >= gs.interval_start " +
                "                       AND lock.timestamp < gs.interval_start + " +
                "                                              ? " +
                "                       AND lock.id = ? " +
                "GROUP BY gs.interval_start " +
                "ORDER BY gs.interval_start;";
        var resp = new HashMap<String, Collection<AggregatePerformancePoint>>();
        var handlers = handlerRepository.findAllByComponentComponentName(componentId);

        var tsFrom = from.toInstant().toEpochMilli();
        var toTs = to.toInstant().toEpochMilli();

        for (var handler : handlers) {
            if(handler.getHandlerType() != HandlerType.AggregateCommandHandler) continue;
            var serviceTimeTs = bundleId + "_" + componentId + "_" + handler.getHandledPayload().getName();
            var eventPublicationTs = EVENT_STORE + "_" + EVENT_STORE_COMPONENT + "_" + handler.getReturnType().getName();
            var aggregateRetrieveTs = SERVER + "_" + GATEWAY_COMPONENT + "_" + handler.getHandledPayload().getName();
            var aggregateLockTs = SERVER + "_" + LOCK_COMPONENT + "_" + handler.getHandledPayload().getName();
            var i = interval * 1000;
            resp.put(handler.getHandledPayload().getName(), jdbcTemplate.query(sql, new Object[]{tsFrom, toTs, i, i, serviceTimeTs,
                    i, eventPublicationTs, i, aggregateRetrieveTs, i, aggregateLockTs
            }, (rs, rowNum) -> {
                var pp = new AggregatePerformancePoint();
                pp.setTimestamp(rs.getString("ts"));
                pp.setCount(rs.getBigDecimal("count"));
                pp.setServiceTime(rs.getBigDecimal("serviceTime"));
                pp.setStore(rs.getBigDecimal("store"));
                pp.setRetrieve(rs.getBigDecimal("retrieve"));
                pp.setLock(rs.getBigDecimal("lock"));
                return pp;
            }));
        }
        return resp;
    }


    @SuppressWarnings("Annotator")
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupTelemetry() {
        jdbcTemplate.update("delete from performance__handler_service_time_ts where timestamp < CURRENT_TIMESTAMP  - INTERVAL '" + ttl + " DAY'");
        jdbcTemplate.update("delete from performance__handler_invocation_count_ts where timestamp < CURRENT_TIMESTAMP  - INTERVAL '" + ttl + " DAY'");
    }
}
