package com.evento.server.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Group commit of fenced aggregate appends: the appends that arrive while a batch is being
 * written are written together, in one statement and one transaction, so a burst of commands
 * costs one commit (one WAL flush) instead of one each.
 *
 * <p>Why it matters most under CPES: an append holds the global {@code es-lock} until its commit
 * is durable, so CPES appended one event per commit, about 430 a second on a 4-core machine with
 * a local disk (B1), and one per round trip to a synchronous standby in another region. A batch
 * takes the lock once and gives its events consecutive sequence numbers, committed together:
 * sequence order still equals commit order, so CPES consumers still read without a delay.
 *
 * <p>Batches form by themselves, without a timer: {@code writers} threads each take what is
 * queued (up to {@code maxBatch}), write it, and come back. With one command in flight a batch
 * is one append and costs what an append cost before; with many, the ones queued behind a
 * running batch go in the next one. Under CPES a second writer waits on {@code es-lock} in the
 * database while the first commits, so the lock goes from batch to batch without a client
 * round trip in between.
 *
 * <p>Each row is inserted with {@code ON CONFLICT DO NOTHING}: a row whose version is taken (the
 * aggregate fence) or whose idempotency key is stored does not fail its batch, it is reported
 * not inserted and its caller gets the conflict it would have got alone. A conflicting row of a
 * transaction still in flight is waited for, as with a single insert. Any other failure of a
 * batch is handed to each of its callers ({@link BatchFailedException}), who append alone, so
 * one bad row cannot fail the others.
 */
final class AppendBatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AppendBatcher.class);

    private static final String INPUT =
            "input AS (SELECT * FROM unnest(?::bigint[], ?::text[], ?::text[], ?::text[], ?::text[], ?::bigint[], "
                    + "?::text[], ?::text[]) WITH ORDINALITY AS t(seq, aggregate_id, event_message, event_name, context, "
                    + "aggregate_seq, idempotency_key, idempotency_command, ord))";

    private static final String INSERT_AND_REPORT =
            "ins AS (INSERT INTO es__events (event_sequence_number, aggregate_id, event_message, event_name, context, "
                    + "aggregate_seq, idempotency_key, idempotency_command) "
                    + "SELECT seq, aggregate_id, event_message, event_name, context, aggregate_seq, idempotency_key, "
                    + "idempotency_command FROM numbered ORDER BY ord ON CONFLICT DO NOTHING RETURNING event_sequence_number) "
                    + "SELECT n.ord, n.seq, i.event_sequence_number IS NOT NULL FROM numbered n "
                    + "LEFT JOIN ins i ON i.event_sequence_number = n.seq ORDER BY n.ord";

    /**
     * CPES: {@code es-lock} first (a materialised CTE every later one reads), then the sequence
     * numbers in input order, then the rows. The {@code seq} input column is unused (nulls).
     */
    static final String CPES_BATCH_SQL =
            "WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))), " + INPUT + ", "
                    + "numbered AS (SELECT input.ord, nextval('event_sequence_number_serial') AS seq, input.aggregate_id, "
                    + "input.event_message, input.event_name, input.context, input.aggregate_seq, input.idempotency_key, "
                    + "input.idempotency_command FROM es_lock, input ORDER BY input.ord), " + INSERT_AND_REPORT;

    /** APES: the Snowflake ids come with the rows. */
    static final String APES_BATCH_SQL =
            "WITH " + INPUT + ", numbered AS (SELECT * FROM input), " + INSERT_AND_REPORT;

    /** One queued append. {@code result}: the sequence number, or -1 when not inserted (a conflict). */
    record Pending(String aggregateId, String eventMessage, String eventName, String context, long aggregateSeq,
                   String idempotencyKey, String idempotencyCommand, CompletableFuture<Long> result) {
    }

    /** The batch failed for another reason than a conflict: the caller appends alone. */
    static final class BatchFailedException extends RuntimeException {
        BatchFailedException(Throwable cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final boolean cpes;
    private final LongSupplier ids;
    private final int maxBatch;
    private final LinkedBlockingQueue<Pending> queue = new LinkedBlockingQueue<>();
    private final List<Thread> writers = new ArrayList<>();
    private final AtomicLong batches = new AtomicLong();
    private final AtomicLong appends = new AtomicLong();
    private volatile boolean closed;

    /**
     * @param ids the APES Snowflake ids, drawn when a batch is written (unused under CPES)
     */
    AppendBatcher(JdbcTemplate jdbcTemplate, boolean cpes, LongSupplier ids, int writers, int maxBatch) {
        this.jdbcTemplate = jdbcTemplate;
        this.cpes = cpes;
        this.ids = ids;
        this.maxBatch = Math.max(1, maxBatch);
        for (int i = 0; i < Math.max(1, writers); i++) {
            this.writers.add(Thread.ofVirtual().name("evento-append-" + i).start(this::write));
        }
    }

    /**
     * Queues an append and waits for its batch.
     *
     * @return the event's sequence number, or -1 when a conflicting row (the version or the key)
     * kept it out
     * @throws BatchFailedException when its batch failed otherwise
     */
    long append(String aggregateId, String eventMessage, String eventName, String context, long aggregateSeq,
                String idempotencyKey, String idempotencyCommand) {
        if (closed) throw new BatchFailedException(new IllegalStateException("the append batcher is closed"));
        var pending = new Pending(aggregateId, eventMessage, eventName, context, aggregateSeq, idempotencyKey,
                idempotencyCommand, new CompletableFuture<>());
        queue.add(pending);
        try {
            return pending.result().join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    /** Batches written so far. */
    long batchCount() {
        return batches.get();
    }

    /** Appends written so far (inserted or not). */
    long appendCount() {
        return appends.get();
    }

    private void write() {
        var batch = new ArrayList<Pending>(maxBatch);
        while (!closed) {
            try {
                var first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, maxBatch - 1);
                writeBatch(batch);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                // writeBatch completes every future; this is only a guard for the loop.
                log.error("event=append_batch_writer_error", t);
                for (var p : batch) p.result().completeExceptionally(new BatchFailedException(t));
            } finally {
                batch.clear();
            }
        }
        // Closing: nobody writes what is left, its callers append alone.
        Pending left;
        while ((left = queue.poll()) != null) {
            left.result().completeExceptionally(new BatchFailedException(new IllegalStateException("closed")));
        }
    }

    private void writeBatch(List<Pending> batch) {
        int n = batch.size();
        var seqs = new Long[n];
        var aggregateIds = new String[n];
        var messages = new String[n];
        var names = new String[n];
        var contexts = new String[n];
        var versions = new Long[n];
        var keys = new String[n];
        var commands = new String[n];
        for (int i = 0; i < n; i++) {
            var p = batch.get(i);
            seqs[i] = cpes ? null : ids.getAsLong();
            aggregateIds[i] = p.aggregateId();
            messages[i] = p.eventMessage();
            names[i] = p.eventName();
            contexts[i] = p.context();
            versions[i] = p.aggregateSeq();
            keys[i] = p.idempotencyKey();
            commands[i] = p.idempotencyCommand();
        }
        long[] results = new long[n];
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) c -> {
                try (var ps = c.prepareStatement(cpes ? CPES_BATCH_SQL : APES_BATCH_SQL)) {
                    ps.setArray(1, array(c, "bigint", seqs));
                    ps.setArray(2, array(c, "text", aggregateIds));
                    ps.setArray(3, array(c, "text", messages));
                    ps.setArray(4, array(c, "text", names));
                    ps.setArray(5, array(c, "text", contexts));
                    ps.setArray(6, array(c, "bigint", versions));
                    ps.setArray(7, array(c, "text", keys));
                    ps.setArray(8, array(c, "text", commands));
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int ord = rs.getInt(1) - 1;
                            results[ord] = rs.getBoolean(3) ? rs.getLong(2) : -1L;
                        }
                    }
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("event=append_batch_failed size={} action=append_alone error=\"{}\"", n, e.getMessage());
            var failed = new BatchFailedException(e);
            for (var p : batch) p.result().completeExceptionally(failed);
            return;
        }
        batches.incrementAndGet();
        appends.addAndGet(n);
        for (int i = 0; i < n; i++) batch.get(i).result().complete(results[i]);
    }

    private static java.sql.Array array(Connection c, String type, Object[] values) throws SQLException {
        return c.createArrayOf(type, values);
    }

    @Override
    public void close() {
        closed = true;
        writers.forEach(Thread::interrupt);
    }
}
