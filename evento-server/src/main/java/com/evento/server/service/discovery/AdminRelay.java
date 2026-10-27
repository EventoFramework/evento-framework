package com.evento.server.service.discovery;

import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.server.bus.BusFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Console actions on a consumer whose instance is connected to another server (G17). The web
 * console of any server shows every consumer of the cluster (the catalog is shared), but its
 * actions (status, retry or delete a dead event, replay the dead queue) go to the instance over the
 * bus, and only the server it is connected to can reach it: elsewhere they failed with "No value
 * present".
 *
 * <p>The database relays them, as it carries everything else between servers (no server calls
 * another): the receiving server finds the owning server in the presence table
 * ({@code core__bundle_instance}), writes the request to {@code core__admin_relay} and waits; the
 * owner picks it up ({@code FOR UPDATE SKIP LOCKED}, every {@code evento.server.admin-relay.poll-ms},
 * 300 by default), forwards it to the instance as a local action, and writes the answer back. A
 * request nobody answers in {@link #TIMEOUT} fails; rows older than an hour are deleted.
 */
@Component
public class AdminRelay implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AdminRelay.class);

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Runs a relayed action on this server: the consumer's instance is connected here. */
    @FunctionalInterface
    public interface LocalAction {
        CompletableFuture<? extends Serializable> run(String consumerId, Serializable body, BusFacade bus);
    }

    private final JdbcTemplate jdbc;
    private final BusFacade bus;
    private final String self;
    private final long staleAfterSeconds;
    private final ScheduledExecutorService waiter = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "evento-admin-relay");
        t.setDaemon(true);
        return t;
    });
    private volatile LocalAction localAction;
    private volatile boolean available = true;
    private volatile long lastCleanup;

    public AdminRelay(JdbcTemplate jdbc, BusFacade bus, BundlePresence presence,
                      @Value("${evento.server.presence.stale-after-ms:180000}") long staleAfterMs) {
        this.jdbc = jdbc;
        this.bus = bus;
        this.self = presence.serverInstanceId();
        this.staleAfterSeconds = Math.max(1, staleAfterMs / 1000);
    }

    /** What this server does with the actions relayed to it. */
    public void serve(LocalAction action) {
        this.localAction = action;
    }

    public String self() {
        return self;
    }

    /** The server another of {@code instanceIds} is connected to, by the presence table; not this one. */
    public Optional<String> ownerOf(Collection<String> instanceIds) {
        if (!available || instanceIds.isEmpty()) return Optional.empty();
        try {
            List<String> owners = jdbc.queryForList(
                    "SELECT server_instance_id FROM core__bundle_instance WHERE instance_id = ANY (?) "
                            + "AND server_instance_id <> ? AND updated_at > now() - make_interval(secs => ?) "
                            + "ORDER BY updated_at DESC LIMIT 1",
                    String.class, instanceIds.toArray(new String[0]), self, staleAfterSeconds);
            return owners.stream().findFirst();
        } catch (RuntimeException e) {
            log.warn("event=admin_relay_owner_lookup_failed error=\"{}\"", e.getMessage());
            return Optional.empty();
        }
    }

    /** Hands {@code body} for {@code consumerId} to {@code owner} and completes with its answer. */
    public CompletableFuture<Serializable> send(String owner, String consumerId, Serializable body) {
        var id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO core__admin_relay (id, server_instance_id, consumer_id, body) VALUES (?, ?, ?, ?)",
                    id, owner, consumerId, ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(body));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "cannot relay the action to server " + owner + ": " + e.getMessage(), e));
        }
        log.info("event=admin_relay_sent id={} owner={} consumerId={} type={}", id, owner, consumerId,
                body.getClass().getSimpleName());
        var answer = new CompletableFuture<Serializable>();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        poll(id, owner, answer, deadline);
        return answer;
    }

    private void poll(UUID id, String owner, CompletableFuture<Serializable> answer, long deadline) {
        waiter.schedule(() -> {
            try {
                var rows = jdbc.queryForList("SELECT status, result FROM core__admin_relay WHERE id = ?", id);
                if (!rows.isEmpty() && !"PENDING".equals(rows.getFirst().get("status"))
                        && !"RUNNING".equals(rows.getFirst().get("status"))) {
                    String status = (String) rows.getFirst().get("status");
                    String result = (String) rows.getFirst().get("result");
                    jdbc.update("DELETE FROM core__admin_relay WHERE id = ?", id);
                    if ("DONE".equals(status)) {
                        answer.complete(ObjectMapperUtils.getPayloadObjectMapper().readValue(result, Serializable.class));
                    } else {
                        answer.completeExceptionally(new IllegalStateException(
                                "server " + owner + " could not run the action: " + result));
                    }
                    return;
                }
                if (System.nanoTime() > deadline) {
                    jdbc.update("DELETE FROM core__admin_relay WHERE id = ? AND status = 'PENDING'", id);
                    answer.completeExceptionally(new IllegalStateException("server " + owner
                            + " did not answer the relayed action within " + TIMEOUT.toSeconds() + " s"));
                    return;
                }
                poll(id, owner, answer, deadline);
            } catch (Exception e) {
                answer.completeExceptionally(e);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    /** Runs the actions relayed to this server. */
    @Scheduled(fixedDelayString = "${evento.server.admin-relay.poll-ms:300}")
    public void runRelayed() {
        var action = localAction;
        if (action == null || !available) return;
        List<java.util.Map<String, Object>> claimed;
        try {
            claimed = jdbc.queryForList(
                    "UPDATE core__admin_relay SET status = 'RUNNING' WHERE id IN (SELECT id FROM core__admin_relay "
                            + "WHERE server_instance_id = ? AND status = 'PENDING' ORDER BY created_at LIMIT 16 "
                            + "FOR UPDATE SKIP LOCKED) RETURNING id, consumer_id, body", self);
        } catch (org.springframework.dao.DataAccessException e) {
            if (e.getMostSpecificCause() instanceof java.sql.SQLException sql && "42P01".equals(sql.getSQLState())) {
                // No table (a schema managed outside the server without V6): the relay is off.
                available = false;
                log.warn("event=admin_relay_unavailable reason=\"no core__admin_relay table\"");
            } else {
                log.debug("event=admin_relay_poll_failed error=\"{}\"", e.getMessage());
            }
            return;
        }
        for (var row : claimed) {
            var id = (UUID) row.get("id");
            var consumerId = (String) row.get("consumer_id");
            CompletableFuture<? extends Serializable> run;
            try {
                var body = ObjectMapperUtils.getPayloadObjectMapper().readValue((String) row.get("body"), Serializable.class);
                run = action.run(consumerId, body, bus);
            } catch (Exception e) {
                run = CompletableFuture.failedFuture(e);
            }
            run.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).whenComplete((result, error) -> {
                try {
                    if (error == null) {
                        jdbc.update("UPDATE core__admin_relay SET status = 'DONE', result = ?, completed_at = now() "
                                + "WHERE id = ?", ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(result), id);
                    } else {
                        jdbc.update("UPDATE core__admin_relay SET status = 'FAILED', result = ?, completed_at = now() "
                                + "WHERE id = ?", String.valueOf(error.getMessage()), id);
                    }
                } catch (Exception e) {
                    log.warn("event=admin_relay_answer_failed id={} error=\"{}\"", id, e.getMessage());
                }
            });
            log.info("event=admin_relay_run id={} consumerId={}", id, consumerId);
        }
        long now = System.currentTimeMillis();
        if (now - lastCleanup > 600_000) {
            lastCleanup = now;
            try {
                jdbc.update("DELETE FROM core__admin_relay WHERE created_at < now() - interval '1 hour'");
            } catch (RuntimeException ignored) {
                // next time
            }
        }
    }

    @Override
    public void close() {
        waiter.shutdownNow();
    }
}
