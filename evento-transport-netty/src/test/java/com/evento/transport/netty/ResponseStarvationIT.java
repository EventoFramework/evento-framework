package com.evento.transport.netty;

import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A request handler that waits for the response to a request of its own (the broker forwarding a
 * command to the handler bundle; a bundle handler sending a nested command) must not deadlock when
 * every thread of a bounded business executor is busy doing the same. Responses used to be queued
 * on that executor, behind the very requests waiting for them: with the server's pool full
 * (8 threads per CPU) no response was ever processed and every command expired (found by the B1
 * benchmark at 128 commands in flight).
 */
class ResponseStarvationIT {

    private NettyServerTransport server;
    private NettyClientTransport client;
    private ExecutorService serverPool;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
        if (serverPool != null) serverPool.shutdownNow();
    }

    @Test
    @Timeout(30)
    void requestsWaitingForResponsesOnAFullBoundedPoolStillComplete() throws Exception {
        int poolSize = 2;
        int inFlight = 8;
        serverPool = Executors.newFixedThreadPool(poolSize);
        var serverConfig = config(serverPool);
        var serverChildren = new CopyOnWriteArrayList<com.evento.transport.Transport>();
        server = new NettyServerTransport(serverConfig);
        server.onConnection(serverChildren::add);
        int port = server.start(0);
        client = new NettyClientTransport("caller", "127.0.0.1", port,
                config(Executors.newVirtualThreadPerTaskExecutor()));

        // The client answers whatever the server asks, and records the server's final answers.
        var answers = new ConcurrentHashMap<UUID, CompletableFuture<Response>>();
        client.onMessage(message -> {
            if (message instanceof Request asked) {
                client.send(Response.success(asked.correlationId(), "Pong", new byte[]{1}));
            } else if (message instanceof Response answer) {
                var f = answers.remove(answer.correlationId());
                if (f != null) f.complete(answer);
            }
        });
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        var child = serverChildren.getFirst();

        // The server, for each request, asks the client something and blocks on the answer, on a
        // thread of its two-thread pool: the broker's forward, in miniature.
        var nested = new ConcurrentHashMap<UUID, CompletableFuture<Response>>();
        child.onMessage(message -> {
            if (message instanceof Response answer) {
                var f = nested.remove(answer.correlationId());
                if (f != null) f.complete(answer);
                return;
            }
            if (!(message instanceof Request request)) return;
            var ask = UUID.randomUUID();
            var reply = new CompletableFuture<Response>();
            nested.put(ask, reply);
            child.send(new Request(ask, "server", "s1", "1", "Ping", new byte[]{0}, 5_000L,
                    System.currentTimeMillis()));
            try {
                reply.get(10, TimeUnit.SECONDS);
                child.send(Response.success(request.correlationId(), "Done", new byte[]{2}));
            } catch (Exception e) {
                child.send(Response.success(request.correlationId(), "Starved", new byte[]{3}));
            }
        });

        var pending = new ArrayList<CompletableFuture<Response>>();
        for (int i = 0; i < inFlight; i++) {
            var id = UUID.randomUUID();
            var f = new CompletableFuture<Response>();
            answers.put(id, f);
            pending.add(f);
            client.send(new Request(id, "caller", "c1", "1", "Work", new byte[]{0}, 5_000L,
                    System.currentTimeMillis()));
        }
        for (var f : pending) {
            assertThat(f.get(20, TimeUnit.SECONDS).payloadType()).isEqualTo("Done");
        }
    }

    private static NettyTransportConfig config(java.util.concurrent.Executor executor) {
        return new NettyTransportConfig(
                Duration.ofMillis(500), Duration.ofMillis(5000), Duration.ofSeconds(5),
                16 * 1024 * 1024, 64 * 1024, 32 * 1024,
                new com.evento.transport.reconnect.ExponentialBackoffWithJitter(),
                new com.evento.transport.codec.JacksonCborCodec(),
                executor);
    }
}
