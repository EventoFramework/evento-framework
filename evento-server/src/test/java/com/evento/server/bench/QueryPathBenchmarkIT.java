package com.evento.server.bench;

import com.evento.application.client.BundleClient;
import com.evento.application.client.handler.HandlerRegistry;
import com.evento.application.client.stream.DirectStreamConfig;
import com.evento.application.client.stream.InboundStream;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.transport.codec.PayloadEncoding;
import com.evento.transport.netty.NettyTransportConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Mini benchmark of the query path: what an evento-server spends to carry a query answer,
 * by answer size and by mode, measured on a broker running alone in its own JVM
 * ({@link BenchBroker}). Off unless {@code EVENTO_RUN_BENCH=true}; writes
 * {@code build/bench/query-path.md}.
 *
 * <ul>
 *   <li>A — broker resources per answer: plain reply as 2.5.2 handled it (cached for replay),
 *       plain reply on 2.6 (straight), stream through the broker, direct stream.</li>
 *   <li>B — what the broker still holds after a burst of queries.</li>
 *   <li>C — the largest answer a broker with a 256 MiB heap survives, plain vs streamed.</li>
 *   <li>D — latency of a small query answered by the bundle itself vs through the broker.</li>
 *   <li>E — reply compression on a compressible answer.</li>
 * </ul>
 *
 * <p>The "2.5.2" mode reproduces the cache behaviour of 2.5.2 (every reply retained for
 * replay, bounded by entries only) on the 2.6 transport: it does not include the copies
 * 2.5.2 also made while reassembling chunks, so it understates the old cost.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_BENCH", matches = "true")
@Timeout(value = 60, unit = TimeUnit.MINUTES)
class QueryPathBenchmarkIT {

    private static final String EXPORT = "ExportQuery";
    private static final int MB = 1024 * 1024;
    private static final int FRAME = 256 * 1024;
    private static final int REPEATS = 3;
    private static final byte[] RANDOM = new byte[4 * MB];
    private static final byte[] JSON;

    static {
        new Random(42).nextBytes(RANDOM);
        var sb = new StringBuilder();
        var rnd = new Random(7);
        while (sb.length() < 4 * MB) {
            sb.append("{\"orderId\":\"ORD-").append(100000 + rnd.nextInt(900000))
                    .append("\",\"customer\":\"CUST-").append(rnd.nextInt(5000))
                    .append("\",\"status\":\"").append(rnd.nextBoolean() ? "SHIPPED" : "CREATED")
                    .append("\",\"total\":").append(rnd.nextInt(100000) / 100.0).append("},\n");
        }
        JSON = Arrays.copyOf(sb.toString().getBytes(StandardCharsets.UTF_8), 4 * MB);
    }

    private final StringBuilder report = new StringBuilder();

    // ------------------------------------------------------------------ broker process

    /** The broker JVM. */
    private static final class Broker implements AutoCloseable {
        final Process process;
        final BufferedReader out;
        final PrintWriter in;
        final int port;

        Broker(String heap, boolean legacy) throws Exception {
            int free;
            try (var socket = new ServerSocket(0)) {
                free = socket.getLocalPort();
            }
            var java = ProcessHandle.current().info().command().orElse("java");
            var cmd = new ArrayList<>(List.of(java, "-Xms" + heap, "-Xmx" + heap, "-XX:+UseG1GC",
                    "-XX:+ExitOnOutOfMemoryError", "-cp", System.getProperty("java.class.path"),
                    BenchBroker.class.getName(), String.valueOf(free)));
            if (legacy) cmd.add("legacy");
            process = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            out = new BufferedReader(new InputStreamReader(process.getInputStream()));
            in = new PrintWriter(process.getOutputStream(), true);
            String line;
            while ((line = out.readLine()) != null && !line.startsWith("READY")) { /* broker logs */ }
            if (line == null) throw new IllegalStateException("broker did not start");
            port = Integer.parseInt(line.substring("READY ".length()).trim());
        }

        void reset() throws Exception {
            in.println("reset");
            expect("OK");
        }

        Map<String, Long> stats() throws Exception {
            in.println("stats");
            var line = expect("STATS");
            var map = new HashMap<String, Long>();
            for (var kv : line.substring(6).trim().split(" ")) {
                var p = kv.split("=");
                map.put(p[0], Long.parseLong(p[1]));
            }
            return map;
        }

        boolean alive() {
            return process.isAlive();
        }

        private String expect(String prefix) throws Exception {
            String line;
            while ((line = out.readLine()) != null) {
                if (line.startsWith(prefix)) return line;
            }
            throw new IllegalStateException("broker exited (exit=" + exitCode() + ")");
        }

        Integer exitCode() {
            try {
                return process.waitFor(2, TimeUnit.SECONDS) ? process.exitValue() : null;
            } catch (InterruptedException e) {
                return null;
            }
        }

        @Override
        public void close() {
            if (process.isAlive()) {
                in.println("quit");
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                }
            }
        }
    }

    // ------------------------------------------------------------------ bundles

    /** Answers {@code EXPORT}: payload = size (8 bytes) + data kind (0 random, 1 JSON). */
    private static final class Exporter implements HandlerRegistry.StreamingRequestHandler {
        @Override
        public byte[] handle(byte[] payload, HandlerRegistry.RequestContext context) {
            var p = ByteBuffer.wrap(payload);
            int size = (int) p.getLong();
            var source = p.get() == 1 ? JSON : RANDOM;
            var answer = new byte[size];
            for (int off = 0; off < size; off += source.length) {
                System.arraycopy(source, 0, answer, off, Math.min(source.length, size - off));
            }
            return answer;
        }

        @Override
        public void handleStream(byte[] payload, HandlerRegistry.RequestContext context,
                                 HandlerRegistry.StreamSink sink) throws Exception {
            var p = ByteBuffer.wrap(payload);
            long size = p.getLong();
            var source = p.get() == 1 ? JSON : RANDOM;
            for (long sent = 0; sent < size; sent += FRAME) {
                int len = (int) Math.min(FRAME, size - sent);
                int from = (int) (sent % source.length);
                if (from + len > source.length) from = 0;
                sink.send(Arrays.copyOfRange(source, from, from + len));
            }
        }
    }

    private static byte[] ask(long size, boolean json) {
        return ByteBuffer.allocate(9).putLong(size).put((byte) (json ? 1 : 0)).array();
    }

    private static BundleClient handler(int port, boolean declaredQuery, DirectStreamConfig direct, boolean shortCircuit) {
        var b = BundleClient.builder("reports", "reports-1")
                .host("127.0.0.1").port(port)
                .handlerPayloadTypes(List.of(EXPORT))
                .directStreams(direct)
                .localQueryShortCircuit(shortCircuit)
                .defaultRequestTimeout(Duration.ofSeconds(120))
                .transportConfig(NettyTransportConfig.defaults());
        if (declaredQuery) {
            b.registeredHandlers(List.of(new RegisteredHandler(ComponentType.Projection, "ExportProjection",
                    HandlerType.QueryHandler, PayloadType.Query, EXPORT, "Multiple", true, null)));
        }
        var client = b.build();
        client.registerRequestHandler(EXPORT, new Exporter());
        return client;
    }

    private static BundleClient caller(int port, boolean acceptCompressed) {
        return BundleClient.builder("web", "web-1")
                .host("127.0.0.1").port(port)
                .handlerPayloadTypes(List.of())
                .acceptCompressedReplies(acceptCompressed)
                .defaultRequestTimeout(Duration.ofSeconds(120))
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    private static void startAll(BundleClient... clients) throws Exception {
        for (var c : clients) c.start().get(10, TimeUnit.SECONDS);
        Thread.sleep(500);
    }

    // ------------------------------------------------------------------ operations

    private enum Mode {
        PLAIN_252("plain reply, 2.5.2 cache"), PLAIN("plain reply, 2.6"), STREAM("stream via broker"),
        DIRECT("direct stream");
        final String label;
        Mode(String label) { this.label = label; }
    }

    /** Asks one answer of {@code size} bytes; returns the bytes received. */
    private static long once(BundleClient web, Mode mode, long size, boolean json) throws Exception {
        if (mode == Mode.PLAIN_252 || mode == Mode.PLAIN) {
            var r = web.request(EXPORT, ask(size, json), Duration.ofSeconds(120), mode == Mode.PLAIN)
                    .get(180, TimeUnit.SECONDS);
            if (r.isError()) throw new IllegalStateException(r.error().exceptionClassName() + ": " + r.error().message());
            return r.payload().length;
        }
        long received = 0;
        try (var stream = web.openStream(EXPORT, ask(size, json), Duration.ofSeconds(60), 16, mode == Mode.DIRECT)) {
            while (true) {
                var item = stream.next();
                if (item instanceof InboundStream.Item.End) return received;
                if (item instanceof InboundStream.Item.Frame(byte[] payload)) received += payload.length;
                if (item instanceof InboundStream.Item.Whole(byte[] payload)) received += payload.length;
            }
        }
    }

    private record Sample(long clientNanos, Map<String, Long> broker) {}

    private static Sample measure(Broker broker, LongSupplier op) throws Exception {
        broker.reset();
        long start = System.nanoTime();
        op.getAsLong();
        long elapsed = System.nanoTime() - start;
        return new Sample(elapsed, broker.stats());
    }

    private static long median(List<Long> values) {
        var sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private static String mib(long bytes) {
        return String.format("%.1f", bytes / (double) MB);
    }

    private static String ms(long nanos) {
        return String.format("%.0f", nanos / 1e6);
    }

    private static LongSupplier unchecked(java.util.concurrent.Callable<Long> c) {
        return () -> {
            try {
                return c.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    // ------------------------------------------------------------------ the benchmark

    @Test
    void queryPath() throws Exception {
        report.append("# Query path benchmark (evento 2.6)\n\n")
                .append("Run ").append(LocalDateTime.now().withNano(0)).append(", JDK ")
                .append(Runtime.version()).append(", ").append(Runtime.getRuntime().availableProcessors())
                .append(" CPUs. Broker alone in its own JVM (G1); bundles in the test JVM; loopback TCP. ")
                .append("Median of ").append(REPEATS).append(" runs. Broker metrics: `peak` = highest heap ")
                .append("sampled every 2 ms above the post-GC baseline; `alloc` = bytes allocated by the broker's ")
                .append("threads; `cpu` = broker process CPU; `held` = heap still used after a full GC.\n\n");

        scenarioA();
        scenarioB();
        scenarioC();
        scenarioD();
        scenarioE();

        var out = Path.of("build", "bench", "query-path.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);
    }

    /** A — broker resources per answer, by size and mode. */
    private void scenarioA() throws Exception {
        report.append("## A. Broker resources per answer (incompressible data)\n\n")
                .append("| Answer | Mode | Client time ms | Throughput MiB/s | Broker peak heap MiB | ")
                .append("Broker alloc MiB | Broker CPU ms | Held after GC MiB |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        long[] sizes = {1L * MB, 8L * MB, 32L * MB, 64L * MB};
        for (var mode : Mode.values()) {
            try (var broker = new Broker("1g", mode == Mode.PLAIN_252)) {
                var handler = handler(broker.port, mode != Mode.PLAIN_252,
                        mode == Mode.DIRECT ? DirectStreamConfig.on(0, "127.0.0.1") : null, false);
                var web = caller(broker.port, false);
                try (handler; web) {
                    startAll(handler, web);
                    once(web, mode, MB, false);  // warm-up
                    for (long size : sizes) {
                        var samples = new ArrayList<Sample>();
                        for (int i = 0; i < REPEATS; i++) {
                            samples.add(measure(broker, unchecked(() -> once(web, mode, size, false))));
                        }
                        long client = median(samples.stream().map(Sample::clientNanos).toList());
                        report.append("| ").append(size / MB).append(" MiB | ").append(mode.label)
                                .append(" | ").append(ms(client))
                                .append(" | ").append(String.format("%.0f", (size / (double) MB) / (client / 1e9)))
                                .append(" | ").append(mib(median(samples.stream().map(s -> s.broker().get("peak")).toList())))
                                .append(" | ").append(mib(median(samples.stream().map(s -> s.broker().get("alloc")).toList())))
                                .append(" | ").append(ms(median(samples.stream().map(s -> s.broker().get("cpu")).toList())))
                                .append(" | ").append(mib(Math.max(0, median(samples.stream().map(s -> s.broker().get("retained")).toList()))))
                                .append(" |\n");
                    }
                }
            }
        }
        report.append("\n");
    }

    /** B — what the broker still holds after a burst of queries. */
    private void scenarioB() throws Exception {
        report.append("## B. Broker memory held after 25 queries of 4 MiB\n\n")
                .append("| Broker | Dedup cache MiB | Heap held after GC MiB |\n|---|---|---|\n");
        for (boolean legacy : new boolean[]{true, false}) {
            try (var broker = new Broker("1g", legacy)) {
                var handler = handler(broker.port, !legacy, null, false);
                var web = caller(broker.port, false);
                try (handler; web) {
                    startAll(handler, web);
                    var sample = measure(broker, unchecked(() -> {
                        for (int i = 0; i < 25; i++) once(web, legacy ? Mode.PLAIN_252 : Mode.PLAIN, 4L * MB, false);
                        return 0L;
                    }));
                    report.append("| ").append(legacy ? "2.5.2 cache (query replies retained 5 min)" : "2.6 (queries straight)")
                            .append(" | ").append(mib(sample.broker().get("dedup")))
                            .append(" | ").append(mib(Math.max(0, sample.broker().get("retained")))).append(" |\n");
                }
            }
        }
        report.append("\n");
    }

    /**
     * C — the largest answer a broker survives, by heap. The peak heap of A counts garbage
     * not yet collected (a 1 GiB heap rarely collects); a small heap shows the live set:
     * a plain reply must fit whole, a stream only its window.
     */
    private void scenarioC() throws Exception {
        report.append("## C. Does the answer get through a broker with a small heap?\n\n")
                .append("| Broker heap | Answer | Mode | Outcome | Broker peak heap MiB |\n|---|---|---|---|---|\n");
        for (long size : new long[]{32L * MB, 64L * MB, 96L * MB, 128L * MB}) {
            report.append(attempt("256m", Mode.PLAIN, size));
        }
        report.append(attempt("256m", Mode.STREAM, 1024L * MB));
        for (long size : new long[]{8L * MB, 16L * MB, 32L * MB}) {
            report.append(attempt("64m", Mode.PLAIN, size));
        }
        report.append(attempt("64m", Mode.STREAM, 1024L * MB));
        report.append("\n");
    }

    private String attempt(String heap, Mode mode, long size) throws Exception {
        try (var broker = new Broker(heap, false)) {
            var handler = handler(broker.port, true, null, false);
            var web = caller(broker.port, false);
            try (handler; web) {
                startAll(handler, web);
                broker.reset();
                String outcome;
                try {
                    once(web, mode, size, false);
                    outcome = "delivered";
                } catch (Exception e) {
                    var cause = e.getCause() == null ? e : e.getCause();
                    outcome = "failed: " + cause.getClass().getSimpleName();
                }
                String peak = "-";
                if (broker.alive()) {
                    peak = mib(broker.stats().get("peak"));
                } else {
                    outcome += " (broker JVM died, exit " + broker.exitCode() + ", OutOfMemoryError)";
                }
                return "| " + heap + " | " + size / MB + " MiB | " + mode.label + " | " + outcome + " | " + peak + " |\n";
            }
        }
    }

    /** D — latency of a small query: answered by the bundle itself vs through the broker. */
    private void scenarioD() throws Exception {
        report.append("## D. Small query (1 KiB answer), 3000 sequential calls\n\n")
                .append("| Path | p50 µs | p95 µs | p99 µs | Broker CPU ms |\n|---|---|---|---|---|\n");
        for (boolean local : new boolean[]{false, true}) {
            try (var broker = new Broker("1g", false)) {
                var orders = handler(broker.port, true, null, local);
                try (orders) {
                    startAll(orders);
                    for (int i = 0; i < 500; i++) {
                        orders.request(EXPORT, ask(1024, false), Duration.ofSeconds(10), true).get(10, TimeUnit.SECONDS);
                    }
                    var latencies = new long[3000];
                    broker.reset();
                    for (int i = 0; i < latencies.length; i++) {
                        long t = System.nanoTime();
                        orders.request(EXPORT, ask(1024, false), Duration.ofSeconds(10), true).get(10, TimeUnit.SECONDS);
                        latencies[i] = System.nanoTime() - t;
                    }
                    var stats = broker.stats();
                    Arrays.sort(latencies);
                    report.append("| ").append(local ? "answered by the bundle itself (2.6)" : "through the broker")
                            .append(" | ").append(latencies[1500] / 1000)
                            .append(" | ").append(latencies[2850] / 1000)
                            .append(" | ").append(latencies[2970] / 1000)
                            .append(" | ").append(ms(stats.get("cpu"))).append(" |\n");
                }
            }
        }
        report.append("\n");
    }

    /** E — compression of a compressible answer. */
    private void scenarioE() throws Exception {
        long size = 16L * MB;
        var encoded = PayloadEncoding.deflateIfWorthwhile(new Exporter().handle(ask(size, true), null),
                PayloadEncoding.DEFAULT_THRESHOLD);
        report.append("## E. Compression, 16 MiB JSON-like answer (plain reply)\n\n")
                .append("On the wire: ").append(mib(size)).append(" MiB plain, ").append(mib(encoded.length))
                .append(" MiB compressed (").append(String.format("%.1f", size / (double) encoded.length))
                .append("x).\n\n")
                .append("| Caller accepts compression | Client time ms | Broker peak heap MiB | Broker alloc MiB | Broker CPU ms |\n")
                .append("|---|---|---|---|---|\n");
        for (boolean accept : new boolean[]{false, true}) {
            try (var broker = new Broker("1g", false)) {
                var handler = handler(broker.port, true, null, false);
                var web = caller(broker.port, accept);
                try (handler; web) {
                    startAll(handler, web);
                    once(web, Mode.PLAIN, MB, true);
                    var samples = new ArrayList<Sample>();
                    for (int i = 0; i < REPEATS; i++) {
                        samples.add(measure(broker, unchecked(() -> once(web, Mode.PLAIN, size, true))));
                    }
                    report.append("| ").append(accept ? "yes" : "no")
                            .append(" | ").append(ms(median(samples.stream().map(Sample::clientNanos).toList())))
                            .append(" | ").append(mib(median(samples.stream().map(s -> s.broker().get("peak")).toList())))
                            .append(" | ").append(mib(median(samples.stream().map(s -> s.broker().get("alloc")).toList())))
                            .append(" | ").append(ms(median(samples.stream().map(s -> s.broker().get("cpu")).toList())))
                            .append(" |\n");
                }
            }
        }
        report.append("\n");
    }
}
