package com.evento.transport.netty;

import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Event-loop construction isolated here for one load-bearing reason: it must use
 * APIs that exist in <b>Netty 4.1</b>. evento-bundle runs inside user Spring Boot
 * applications, and Boot's dependency management pins {@code io.netty:*} to the
 * 4.1 line regardless of what this module declares — Netty 4.2's replacement
 * ({@code MultiThreadIoEventLoopGroup} + {@code NioIoHandler}) does not exist
 * there. Using it made every unpinned Boot app fail bundle startup with
 * {@code NoClassDefFoundError} (shipped in 2.4.4, caught on the live demo).
 * {@link NioEventLoopGroup} is deprecated in 4.2 but present and functional in
 * both lines; the suppression is deliberate and must stay until the framework
 * can require a Netty 4.2 baseline from user applications.
 *
 * <p>Public so that code outside this module that needs an event loop of its own
 * (the bundle's fail-back probe) goes through the same 4.1-safe construction
 * instead of repeating it.
 */
public final class NettyEventLoops {

    private NettyEventLoops() {
    }

    @SuppressWarnings("deprecation")
    static NioEventLoopGroup newNioEventLoopGroup(int threads) {
        return new NioEventLoopGroup(threads);
    }

    /**
     * A NIO event loop group whose threads come from {@code threadFactory}, built
     * with the constructor that exists in Netty 4.1 and 4.2 alike.
     */
    @SuppressWarnings("deprecation")
    public static io.netty.channel.EventLoopGroup newNioEventLoopGroup(int threads,
                                                                     java.util.concurrent.ThreadFactory threadFactory) {
        return new NioEventLoopGroup(threads, threadFactory);
    }
}
