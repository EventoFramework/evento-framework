package com.evento.transport.netty;

import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The notifications of one connection are handled in the order they were sent. A bundle starts
 * with three: its registration, its discovery and "enable"; handled concurrently, "enable" could
 * make the bundle available before the broker had read its discovery and registered its command
 * handling, and a command sent in that window reached the bundle undecorated and failed.
 */
class NotificationOrderIT {

    private NettyServerTransport server;
    private NettyClientTransport client;
    private ExecutorService serverPool;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
        if (serverPool != null) serverPool.shutdownNow();
    }

    private static NettyTransportConfig config(java.util.concurrent.Executor executor) {
        return new NettyTransportConfig(
                Duration.ofMillis(500), Duration.ofMillis(5000), Duration.ofSeconds(5),
                16 * 1024 * 1024, 64 * 1024, 32 * 1024,
                new com.evento.transport.reconnect.ExponentialBackoffWithJitter(),
                new com.evento.transport.codec.JacksonCborCodec(),
                executor);
    }

    @Test
    @Timeout(30)
    void notificationsOfOneConnectionAreHandledInOrderEvenWhenOneIsSlow() throws Exception {
        serverPool = Executors.newFixedThreadPool(8);
        var children = new CopyOnWriteArrayList<com.evento.transport.Transport>();
        server = new NettyServerTransport(config(serverPool));
        server.onConnection(children::add);
        int port = server.start(0);
        client = new NettyClientTransport("bundle", "127.0.0.1", port, config(Executors.newVirtualThreadPerTaskExecutor()));
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !children.isEmpty());

        var handled = new CopyOnWriteArrayList<String>();
        children.getFirst().onMessage(message -> {
            if (!(message instanceof Notification n)) return;
            // The registration (and, on the server, the discovery) takes a while: catalog writes.
            if (n.payloadType().equals("n-0")) sleep(300);
            handled.add(n.payloadType());
        });

        List<String> sent = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String type = "n-" + i;
            sent.add(type);
            client.send(new Notification(UUID.randomUUID(), type, new byte[0], System.currentTimeMillis()));
        }

        await().atMost(10, TimeUnit.SECONDS).until(() -> handled.size() == sent.size());
        assertThat(handled).containsExactlyElementsOf(sent);
    }

    @Test
    @Timeout(30)
    void aBarrierIsHandledAfterTheNotificationsSentBeforeIt() throws Exception {
        serverPool = Executors.newFixedThreadPool(8);
        var children = new CopyOnWriteArrayList<com.evento.transport.Transport>();
        server = new NettyServerTransport(config(serverPool));
        server.onConnection(children::add);
        int port = server.start(0);
        client = new NettyClientTransport("bundle", "127.0.0.1", port, config(Executors.newVirtualThreadPerTaskExecutor()));
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !children.isEmpty());

        var handled = new CopyOnWriteArrayList<String>();
        children.getFirst().onMessage(message -> {
            if (message instanceof Notification n) {
                sleep(300);  // the discovery: catalog writes
                handled.add(n.payloadType());
            } else if (message instanceof Request r) {
                handled.add(r.payloadType());
            }
        });

        client.send(new Notification(UUID.randomUUID(), "discovery", new byte[0], System.currentTimeMillis()));
        client.send(new Request(UUID.randomUUID(), "caller", "c1", "1",
                com.evento.transport.protocol.ProtocolPayloadTypes.BARRIER, new byte[0], 10_000L,
                System.currentTimeMillis()));

        await().atMost(10, TimeUnit.SECONDS).until(() -> handled.size() == 2);
        assertThat(handled).containsExactly("discovery", com.evento.transport.protocol.ProtocolPayloadTypes.BARRIER);
    }

    @Test
    @Timeout(30)
    void requestsStillRunConcurrently() throws Exception {
        serverPool = Executors.newFixedThreadPool(8);
        var children = new CopyOnWriteArrayList<com.evento.transport.Transport>();
        server = new NettyServerTransport(config(serverPool));
        server.onConnection(children::add);
        int port = server.start(0);
        client = new NettyClientTransport("bundle", "127.0.0.1", port, config(Executors.newVirtualThreadPerTaskExecutor()));
        var answered = new CopyOnWriteArrayList<UUID>();
        client.onMessage(m -> {
            if (m instanceof Response r) answered.add(r.correlationId());
        });
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !children.isEmpty());
        var child = children.getFirst();
        child.onMessage(message -> {
            if (!(message instanceof Request r)) return;
            sleep(500);
            child.send(Response.success(r.correlationId(), "Pong", new byte[]{1}));
        });

        long start = System.nanoTime();
        for (int i = 0; i < 4; i++) {
            client.send(new Request(UUID.randomUUID(), "caller", "c1", "1", "Work", new byte[]{0}, 10_000L,
                    System.currentTimeMillis()));
        }
        await().atMost(10, TimeUnit.SECONDS).until(() -> answered.size() == 4);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).as("not one after the other").isLessThan(Duration.ofMillis(1500));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
