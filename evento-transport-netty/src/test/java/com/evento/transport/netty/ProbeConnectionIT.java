package com.evento.transport.netty;

import com.evento.transport.message.Notification;
import com.evento.transport.message.Ping;
import com.evento.transport.message.Pong;
import com.evento.transport.state.ConnectionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The transport pieces a health probe (the bundle's fail-back probe, N16)
 * depends on, over real TCP: a client closed while its connect is still in
 * flight must not leave a socket behind, and a server that is not ready must
 * not answer a Ping from a connection that has sent nothing else.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ProbeConnectionIT {

    private final List<AutoCloseable> closeables = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (var c : closeables.reversed()) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    private static NettyTransportConfig config() {
        return NettyTransportConfig.defaults();
    }

    /**
     * Before the fix, close() on a transport whose connect had not completed
     * found {@code channel == null} and closed nothing; the connect then
     * completed and its channel stayed open for good. Each round wedges the
     * shared event loop so that close() runs while the connect is provably still
     * pending, then lets it go.
     */
    @Test
    void closingWhileTheConnectIsInFlightLeavesNoSocketOpen() throws Exception {
        var group = NettyEventLoops.newNioEventLoopGroup(1, r -> {
            var t = new Thread(r, "probe-it-loop");
            t.setDaemon(true);
            return t;
        });
        closeables.add(() -> group.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS).await(2, TimeUnit.SECONDS));
        var server = new ServerSocket(0, 100, InetAddress.getLoopbackAddress());
        closeables.add(server);
        var accepted = new CopyOnWriteArrayList<Socket>();
        Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try {
                    accepted.add(server.accept());
                } catch (IOException e) {
                    return;
                }
            }
        });

        int rounds = 20;
        var connects = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < rounds; i++) {
            var release = new CountDownLatch(1);
            group.execute(() -> {
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            });
            var transport = new NettyClientTransport("probe-" + i, "127.0.0.1", server.getLocalPort(),
                    config(), group).quietLifecycleLogging();
            connects.add(transport.connect());
            // Released from elsewhere: close() waits (bounded) on the wedged loop.
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                release.countDown();
            });
            transport.close();
            assertThat(transport.state()).isEqualTo(ConnectionState.CLOSED);
        }

        // Whatever the connects did, none of them is a usable connection now...
        for (var c : connects) {
            await().atMost(Duration.ofSeconds(5)).until(c::isDone);
            assertThat(c).isCompletedExceptionally();
        }
        // ...and every connection the server accepted has been closed by the client.
        Thread.sleep(300);
        int leaked = 0;
        for (var s : accepted) {
            if (!closedByPeer(s)) leaked++;
            s.close();
        }
        assertThat(leaked).as("client sockets left open out of %d accepted", accepted.size()).isZero();
    }

    private static boolean closedByPeer(Socket s) throws IOException {
        s.setSoTimeout(2_000);
        try (InputStream in = s.getInputStream()) {
            return in.read() == -1;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException reset) {
            return true;
        }
    }

    @Test
    void aServerThatIsNotReadyLeavesAProbePingUnansweredButNotASessionPing() throws Exception {
        var ready = new AtomicBoolean(false);
        var server = new NettyServerTransport(config());
        server.onConnection(child -> {});
        server.setPreSessionReadiness(ready::get);
        int port = server.start(0);
        closeables.add(server::stop);

        // A probe: nothing but a Ping.
        var probe = new NettyClientTransport("probe", "127.0.0.1", port, config());
        closeables.add(probe);
        var probePongs = new CopyOnWriteArrayList<UUID>();
        probe.onMessage(m -> { if (m instanceof Pong p) probePongs.add(p.correlationId()); });
        probe.connect().get(5, TimeUnit.SECONDS);

        // A session: something else first (a Hello, in the real protocol), then a Ping.
        var session = new NettyClientTransport("session", "127.0.0.1", port, config());
        closeables.add(session);
        var sessionPongs = new CopyOnWriteArrayList<UUID>();
        session.onMessage(m -> { if (m instanceof Pong p) sessionPongs.add(p.correlationId()); });
        session.connect().get(5, TimeUnit.SECONDS);
        session.send(new Notification(UUID.randomUUID(), "test.hello", new byte[0], System.currentTimeMillis()))
                .get(5, TimeUnit.SECONDS);

        var notReadyPing = UUID.randomUUID();
        probe.send(new Ping(notReadyPing, 1, System.currentTimeMillis())).get(5, TimeUnit.SECONDS);
        var sessionPing = UUID.randomUUID();
        session.send(new Ping(sessionPing, 1, System.currentTimeMillis())).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(5)).until(() -> sessionPongs.contains(sessionPing));
        Thread.sleep(500);
        assertThat(probePongs).as("a not-ready server answered a probe").isEmpty();

        ready.set(true);
        var readyPing = UUID.randomUUID();
        probe.send(new Ping(readyPing, 2, System.currentTimeMillis())).get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> probePongs.contains(readyPing));
        assertThat(probePongs).doesNotContain(notReadyPing);
    }

    @Test
    void withoutAGateEveryPingIsAnswered() throws Exception {
        var server = new NettyServerTransport(config());
        server.onConnection(child -> {});
        int port = server.start(0);
        closeables.add(server::stop);
        var probe = new NettyClientTransport("probe", "127.0.0.1", port, config());
        closeables.add(probe);
        var pongs = new CopyOnWriteArrayList<UUID>();
        probe.onMessage(m -> { if (m instanceof Pong p) pongs.add(p.correlationId()); });
        probe.connect().get(5, TimeUnit.SECONDS);
        var ping = UUID.randomUUID();
        probe.send(new Ping(ping, 1, System.currentTimeMillis())).get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> pongs.contains(ping));
    }
}
