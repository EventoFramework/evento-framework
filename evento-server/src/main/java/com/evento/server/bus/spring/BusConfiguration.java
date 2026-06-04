package com.evento.server.bus.spring;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.TransportServer;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring wiring for the v2 server bus. Exposes the seven collaborator beans
 * plus a {@link BusLifecycle} that owns the Netty-backed {@link TransportServer}
 * and is started/stopped with the Spring context.
 *
 * <p>v2 bus is the production bus as of v2.0.
 */
@Configuration
@EnableConfigurationProperties(BusProperties.class)
public class BusConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BusConfiguration.class);

    @Bean
    public BusEventBus busEventBus() {
        return new BusEventBus();
    }

    @Bean
    public ConnectionRegistry connectionRegistry(BusEventBus eventBus) {
        return new ConnectionRegistry(eventBus);
    }

    @Bean
    public ClusterRegistry clusterRegistry(ConnectionRegistry connections) {
        return new ClusterRegistry(connections);
    }

    @Bean(destroyMethod = "")
    public CorrelationStore correlationStore(BusProperties props) {
        return new CorrelationStore(props.correlationCheckInterval());
    }

    @Bean
    public ForwardingTable forwardingTable() {
        return new ForwardingTable();
    }

    @Bean
    public PayloadCodec payloadCodec() {
        return new JacksonCborPayloadCodec();
    }

    /**
     * Bounded platform-thread pool used as the bus business executor. Replaces the
     * previous unbounded virtual-thread-per-task executor, which under high inbound
     * pressure could spawn an unbounded number of tasks and exhaust the Java heap
     * (see OOM seen with many concurrent {@code EventFetchRequest} handlers).
     *
     * <p>Backpressure: when the queue is full, {@code CallerRunsPolicy} forces the
     * Netty event-loop thread to execute the task itself, naturally throttling
     * inbound reads and propagating pressure upstream via TCP.
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor busBusinessExecutor(BusProperties props) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicLong counter = new AtomicLong();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "bus-business-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                props.businessExecutorCoreSize(),
                props.businessExecutorMaxSize(),
                props.businessExecutorKeepAlive().toMillis(),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(props.businessExecutorQueueCapacity()),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(true);
        log.info("event=bus_business_executor_configured core={} max={} queue={} keepAliveMs={}",
                props.businessExecutorCoreSize(),
                props.businessExecutorMaxSize(),
                props.businessExecutorQueueCapacity(),
                props.businessExecutorKeepAlive().toMillis());
        return pool;
    }

    @Bean
    public NettyTransportConfig nettyTransportConfig(BusProperties props,
                                                     ThreadPoolExecutor busBusinessExecutor) {
        return new NettyTransportConfig(
                props.heartbeatWriteIdle(),
                props.heartbeatReadIdle(),
                props.connectTimeout(),
                props.maxFrameLength(),
                props.writeBufferHighWaterMark(),
                props.writeBufferLowWaterMark(),
                new ExponentialBackoffWithJitter(),
                new com.evento.transport.codec.JacksonCborCodec(),
                busBusinessExecutor
        );
    }

    @Bean(destroyMethod = "")
    public TransportServer transportServer(NettyTransportConfig config) {
        return new NettyServerTransport(config);
    }

    /**
     * Wire-level bundle authentication. When {@code evento.server.bus.auth-token} is set, bundles
     * must present exactly that token in their {@code Hello} (constant-time compared); otherwise
     * authentication is disabled ({@code acceptAll}). The default is {@code acceptAll} to preserve
     * the development/trusted-network experience — set the token to harden a deployment.
     */
    @Bean
    public TokenValidator busTokenValidator(@Value("${evento.server.bus.auth-token:}") String authToken) {
        if (authToken == null || authToken.isBlank()) {
            log.warn("event=bus_auth_disabled detail=\"evento.server.bus.auth-token not set; "
                    + "any bundle may register. Set a token to require authentication.\"");
            return TokenValidator.acceptAll();
        }
        log.info("event=bus_auth_enabled mode=shared-secret");
        return TokenValidator.sharedSecret(authToken);
    }

    @Bean(destroyMethod = "")
    public BusLifecycle busLifecycle(TransportServer transportServer,
                                     ConnectionRegistry connections,
                                     ClusterRegistry cluster,
                                     CorrelationStore correlations,
                                     ForwardingTable forwarding,
                                     BusEventBus eventBus,
                                     PayloadCodec payloadCodec,
                                     TokenValidator tokenValidator,
                                     BusProperties props) {
        return new BusLifecycle(transportServer, connections, cluster, correlations,
                forwarding, eventBus, props.serverInstanceId(),
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), payloadCodec, tokenValidator);
    }

    @Bean
    public BusMetricsBinder busMetricsBinder(BusLifecycle bus, CorrelationStore correlations,
                                             ForwardingTable forwarding) {
        return new BusMetricsBinder(bus, correlations, forwarding);
    }

    @Bean
    public BusHealthIndicator busHealthIndicator(BusLifecycle bus) {
        return new BusHealthIndicator(bus);
    }

    @Bean
    public BusStarter busStarter(BusLifecycle lifecycle, BusProperties props,
                                     javax.sql.DataSource dataSource) {
        // dataSource is injected only to establish a destruction-order edge:
        // Spring must destroy BusStarter (→ stop the bus, drain all IO callbacks)
        // before it closes the DataSource / HikariPool.
        return new BusStarter(lifecycle, props);
    }

    /**
     * Owns the {@code start/stop} side-effect on {@link BusLifecycle} so the bean
     * itself stays free of {@code @PostConstruct} surprises. Logs the resolved
     * port (useful when {@code port=0} for ephemeral binding in tests).
     */
    public static final class BusStarter {
        private final BusLifecycle lifecycle;
        private final BusProperties props;

        public BusStarter(BusLifecycle lifecycle, BusProperties props) {
            this.lifecycle = lifecycle;
            this.props = props;
        }

        @PostConstruct
        public void start() {
            int port = lifecycle.start(props.port());
            log.info("event=bus_started port={} server_instance={}", port, props.serverInstanceId());
            log.info("Evento server ready — accepting bundle registrations on port {}", port);
        }

        @PreDestroy
        public void stop() {
            lifecycle.stop(props.shutdownDeadline());
        }
    }
}
