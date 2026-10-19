package com.evento.bench.core;

import org.HdrHistogram.Recorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Closed-loop load: {@code concurrency} callers, each sending its next command as soon as the
 * previous one is answered (so at most {@code concurrency} commands are in flight). The same
 * runner drives every system; only {@link OrderCommands} differs.
 *
 * <p>Scenarios:
 * <ul>
 *   <li>{@code create}: every command creates a new order (new aggregate, first event);</li>
 *   <li>{@code spread}: {@code aggregates} orders are created first (not measured), then every
 *       command adds an item to one of them at random (an existing aggregate, low contention);</li>
 *   <li>{@code hot}: one order, every command adds an item to it (maximum contention).</li>
 * </ul>
 * Each concurrency level gets its own fresh orders, so aggregates are equally long for every
 * system. Latency is recorded only for commands that both start and end inside the measured
 * window (after the warm-up); throughput counts the commands answered successfully in it.
 * Refusals and timeouts are counted by exception type, never as throughput.
 */
public final class LoadRunner {

    private final BenchConfig config;
    private final OrderCommands commands;

    public LoadRunner(BenchConfig config, OrderCommands commands) {
        this.config = config;
        this.commands = commands;
    }

    /** Runs every configured concurrency level and appends one JSON line per level to the output. */
    public List<Result> runAll() throws Exception {
        var results = new ArrayList<Result>();
        for (int concurrency : config.concurrencyLevels()) {
            var result = run(concurrency);
            results.add(result);
            System.out.println(result.summary());
            write(result);
        }
        return results;
    }

    Result run(int concurrency) throws Exception {
        String prefix = config.runId() + "-" + config.scenario() + "-c" + concurrency + "-";
        List<String> orders = switch (config.scenario()) {
            case "create" -> List.of();
            case "spread" -> setUp(prefix, config.aggregates());
            case "hot" -> setUp(prefix, 1);
            default -> throw new IllegalArgumentException("unknown scenario " + config.scenario());
        };

        var recorder = new Recorder(3);
        var ok = new LongAdder();
        var errors = new ConcurrentHashMap<String, LongAdder>();
        var created = new AtomicLong();
        long start = System.nanoTime();
        long measureFrom = start + TimeUnit.SECONDS.toNanos(config.warmupSeconds());
        long measureTo = measureFrom + TimeUnit.SECONDS.toNanos(config.durationSeconds());

        var callers = new ArrayList<Thread>();
        for (int i = 0; i < concurrency; i++) {
            callers.add(Thread.ofPlatform().name("caller-" + i).start(() -> {
                var random = ThreadLocalRandom.current();
                while (true) {
                    long sent = System.nanoTime();
                    if (sent >= measureTo) return;
                    CompletableFuture<?> reply = switch (config.scenario()) {
                        case "create" -> commands.create(prefix + created.incrementAndGet());
                        default -> commands.addItem(orders.get(random.nextInt(orders.size())),
                                "sku-" + random.nextInt(1000), 1 + random.nextInt(5));
                    };
                    Throwable failure = null;
                    try {
                        reply.get(config.timeoutMs(), TimeUnit.MILLISECONDS);
                    } catch (ExecutionException e) {
                        failure = e.getCause();
                    } catch (Exception e) {
                        failure = e;
                    }
                    long answered = System.nanoTime();
                    if (sent < measureFrom || answered > measureTo) continue;
                    if (failure == null) {
                        ok.increment();
                        recorder.recordValue(Math.max(1, (answered - sent) / 1_000));
                    } else {
                        errors.computeIfAbsent(name(failure), k -> new LongAdder()).increment();
                    }
                }
            }));
        }
        for (var caller : callers) caller.join();

        var histogram = recorder.getIntervalHistogram();
        var errorCounts = new TreeMap<String, Long>();
        errors.forEach((k, v) -> errorCounts.put(k, v.sum()));
        return new Result(config.system(), config.scenario(), concurrency, config.durationSeconds(), ok.sum(),
                errorCounts, ok.sum() / (double) config.durationSeconds(),
                histogram.getValueAtPercentile(50) / 1000.0, histogram.getValueAtPercentile(90) / 1000.0,
                histogram.getValueAtPercentile(99) / 1000.0, histogram.getValueAtPercentile(99.9) / 1000.0,
                histogram.getMaxValue() / 1000.0, histogram.getMean() / 1000.0, Instant.now().toString());
    }

    /** Creates {@code n} orders, 16 at a time, retrying refused ones; not measured. */
    private List<String> setUp(String prefix, int n) throws Exception {
        var ids = new ArrayList<String>();
        for (int i = 0; i < n; i++) ids.add(prefix + i + "-" + UUID.randomUUID().toString().substring(0, 8));
        int batch = 16;
        for (int from = 0; from < n; from += batch) {
            var pending = new ArrayList<CompletableFuture<?>>();
            for (int i = from; i < Math.min(n, from + batch); i++) pending.add(commands.create(ids.get(i)));
            for (var f : pending) f.get(config.timeoutMs(), TimeUnit.MILLISECONDS);
        }
        return ids;
    }

    private static String name(Throwable failure) {
        Throwable t = failure;
        while ((t instanceof ExecutionException || t instanceof java.util.concurrent.CompletionException)
                && t.getCause() != null) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName();
    }

    private void write(Result result) throws IOException {
        Path out = config.output();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, result.json() + System.lineSeparator(), StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /** One measured concurrency level. Latencies in milliseconds. */
    public record Result(String system, String scenario, int concurrency, int seconds, long ok,
                         Map<String, Long> errors, double throughput, double p50, double p90, double p99,
                         double p999, double max, double mean, String at) {

        String summary() {
            return String.format(Locale.ROOT, "%-22s %-7s c=%-4d %9.1f cmd/s  p50=%7.2f p99=%8.2f max=%8.2f ms  errors=%s",
                    system, scenario, concurrency, throughput, p50, p99, max, errors);
        }

        String json() {
            var errorJson = new StringBuilder("{");
            errors.forEach((k, v) -> errorJson.append(errorJson.length() > 1 ? "," : "")
                    .append('"').append(k).append("\":").append(v));
            errorJson.append('}');
            return String.format(Locale.ROOT,
                    "{\"system\":\"%s\",\"scenario\":\"%s\",\"concurrency\":%d,\"seconds\":%d,\"ok\":%d,"
                            + "\"errors\":%s,\"throughput\":%.1f,\"p50\":%.3f,\"p90\":%.3f,\"p99\":%.3f,"
                            + "\"p999\":%.3f,\"max\":%.3f,\"mean\":%.3f,\"at\":\"%s\"}",
                    system, scenario, concurrency, seconds, ok, errorJson, throughput, p50, p90, p99, p999, max,
                    mean, at);
        }
    }
}
