package com.evento.server.bench;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An evento-server bus alone in its own JVM, so that the benchmark measures the broker's
 * resources and nothing else: heap (sampled every 2 ms), bytes allocated by its threads,
 * CPU time, heap still held after a full GC, and the bytes in its two reply buffers.
 *
 * <p>Requests are handled on a pool of platform threads, as the Spring server does
 * ({@code BusBusinessExecutor}), so that their allocations are counted.
 *
 * <p>Arguments: {@code <port> [legacy]}. {@code legacy} reproduces the 2.5.2 dedup cache
 * (bounded by entries only: every reply retained for 5 minutes). Protocol on stdin/stdout,
 * one line each: {@code reset} → {@code OK}; {@code stats} → {@code STATS k=v ...};
 * {@code quit}.
 */
public final class BenchBroker {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        boolean legacy = args.length > 1 && args[1].equals("legacy");
        var threads = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(32, r -> {
            var t = new Thread(r, "bench-broker-" + threads.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        var config = new NettyTransportConfig(Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH, 1024 * 1024, 256 * 1024, new ExponentialBackoffWithJitter(),
                new JacksonCborCodec(), executor);
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var dedup = legacy
                ? new ForwardingDedupCache(50_000, Duration.ofMinutes(5), Long.MAX_VALUE, Integer.MAX_VALUE)
                : new ForwardingDedupCache();
        var bus = new BusLifecycle(new NettyServerTransport(config), connections, new ClusterRegistry(connections),
                new CorrelationStore(Duration.ofSeconds(1)), new ForwardingTable(), eventBus, "bench-broker",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(), TokenValidator.acceptAll(),
                dedup);
        if (legacy) bus.setReconnectBufferMaxBytes(Long.MAX_VALUE);
        int bound = bus.start(port);

        var memory = ManagementFactory.getMemoryMXBean();
        var threadBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var peak = new AtomicLong();
        var sampler = new Thread(() -> {
            while (true) {
                long used = memory.getHeapMemoryUsage().getUsed();
                peak.accumulateAndGet(used, Math::max);
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "bench-heap-sampler");
        sampler.setDaemon(true);
        sampler.start();

        System.out.println("READY " + bound);
        System.out.flush();
        long baseHeap = 0, baseAlloc = 0, baseCpu = 0, baseRelayed = 0;
        var in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            switch (line.trim()) {
                case "reset" -> {
                    System.gc();
                    Thread.sleep(200);
                    baseHeap = memory.getHeapMemoryUsage().getUsed();
                    peak.set(baseHeap);
                    baseAlloc = threadBean.getTotalThreadAllocatedBytes();
                    baseCpu = os.getProcessCpuTime();
                    baseRelayed = bus.streamBytesRelayed();
                    System.out.println("OK");
                }
                case "stats" -> {
                    long alloc = threadBean.getTotalThreadAllocatedBytes() - baseAlloc;
                    long cpu = os.getProcessCpuTime() - baseCpu;
                    long peakDelta = peak.get() - baseHeap;
                    System.gc();
                    Thread.sleep(200);
                    long retained = memory.getHeapMemoryUsage().getUsed() - baseHeap;
                    System.out.println("STATS peak=" + peakDelta + " alloc=" + alloc + " cpu=" + cpu
                            + " retained=" + retained + " dedup=" + bus.dedupRetainedBytes()
                            + " reconnect=" + bus.reconnectBufferBytes()
                            + " relayed=" + (bus.streamBytesRelayed() - baseRelayed)
                            + " maxHeap=" + memory.getHeapMemoryUsage().getMax());
                }
                case "quit" -> {
                    bus.stop(Duration.ofMillis(200));
                    System.exit(0);
                }
                default -> System.out.println("ERR unknown command");
            }
            System.out.flush();
        }
    }
}
