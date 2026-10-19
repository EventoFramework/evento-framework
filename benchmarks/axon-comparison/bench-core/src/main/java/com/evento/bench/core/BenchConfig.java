package com.evento.bench.core;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The run's parameters, from environment variables (the containers set them):
 * <pre>
 * BENCH_SYSTEM       label written with every result (e.g. evento-cpes, axon-jpa)
 * BENCH_SCENARIO     create | spread | hot
 * BENCH_CONCURRENCY  comma-separated levels, run one after the other (default 1,8,32,128)
 * BENCH_WARMUP_S     seconds before measuring, per level (default 20)
 * BENCH_DURATION_S   measured seconds, per level (default 60)
 * BENCH_AGGREGATES   orders of the spread scenario (default 1000)
 * BENCH_TIMEOUT_MS   how long a caller waits for one reply (default 30000)
 * BENCH_OUT          JSON-lines file the results are appended to (default results/results.jsonl)
 * BENCH_RUN          id prefixed to every order id (default: start time)
 * </pre>
 */
public record BenchConfig(String system, String scenario, List<Integer> concurrencyLevels, int warmupSeconds,
                          int durationSeconds, int aggregates, long timeoutMs, Path output, String runId) {

    public static BenchConfig fromEnvironment() {
        Map<String, String> env = System.getenv();
        return new BenchConfig(
                env.getOrDefault("BENCH_SYSTEM", "unknown"),
                env.getOrDefault("BENCH_SCENARIO", "spread"),
                Arrays.stream(env.getOrDefault("BENCH_CONCURRENCY", "1,8,32,128").split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList(),
                Integer.parseInt(env.getOrDefault("BENCH_WARMUP_S", "20")),
                Integer.parseInt(env.getOrDefault("BENCH_DURATION_S", "60")),
                Integer.parseInt(env.getOrDefault("BENCH_AGGREGATES", "1000")),
                Long.parseLong(env.getOrDefault("BENCH_TIMEOUT_MS", "30000")),
                Path.of(env.getOrDefault("BENCH_OUT", "results/results.jsonl")),
                env.getOrDefault("BENCH_RUN", Long.toString(System.currentTimeMillis(), 36)));
    }
}
