package com.evento.server.es.pg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A TCP proxy that delays every server-to-client byte by a fixed amount, i.e. adds that much to
 * each request/response round trip. With a delay well above the local RTT, the time a
 * database operation takes divided by the delay counts its round trips.
 */
final class DelayProxy implements AutoCloseable {

    private record Chunk(long dueNanos, byte[] bytes) {
    }

    private final ServerSocket server;
    private final String targetHost;
    private final int targetPort;
    private final long delayNanos;
    private volatile boolean closed;

    DelayProxy(String targetHost, int targetPort, long delayMillis) throws IOException {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.delayNanos = delayMillis * 1_000_000L;
        this.server = new ServerSocket();
        this.server.bind(new InetSocketAddress("127.0.0.1", 0));
        Thread.ofPlatform().daemon().name("delay-proxy-accept").start(this::acceptLoop);
    }

    int port() {
        return server.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket client = server.accept();
                client.setTcpNoDelay(true);
                Socket upstream = new Socket(targetHost, targetPort);
                upstream.setTcpNoDelay(true);
                Thread.ofPlatform().daemon().start(() -> pump(client, upstream, 0));
                Thread.ofPlatform().daemon().start(() -> pump(upstream, client, delayNanos));
            } catch (IOException e) {
                if (!closed) e.printStackTrace();
            }
        }
    }

    private static void pump(Socket from, Socket to, long delayNanos) {
        var queue = new LinkedBlockingQueue<Chunk>();
        var writer = Thread.ofPlatform().daemon().start(() -> {
            try (OutputStream out = to.getOutputStream()) {
                while (true) {
                    Chunk c = queue.take();
                    if (c.bytes() == null) return;
                    long wait = c.dueNanos() - System.nanoTime();
                    if (wait > 0) Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                    out.write(c.bytes());
                    out.flush();
                }
            } catch (IOException | InterruptedException ignored) {
                closeQuietly(from);
                closeQuietly(to);
            }
        });
        try (InputStream in = from.getInputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                queue.add(new Chunk(System.nanoTime() + delayNanos, Arrays.copyOf(buf, n)));
            }
        } catch (IOException ignored) {
            // connection closed
        } finally {
            queue.add(new Chunk(0, null));
            try {
                writer.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        server.close();
    }
}
