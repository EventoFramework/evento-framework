package com.evento.server.bus.v2.spring;

import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.lifecycle.BusLifecycle;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Spring wiring for the v2 server bus. Exposes the seven collaborator beans
 * plus a {@link BusLifecycle} that owns the Netty-backed {@link TransportServer}
 * and is started/stopped with the Spring context.
 *
 * <p>v2 bus is the production bus as of v2.0.
 */
@Configuration
@EnableConfigurationProperties(BusV2Properties.class)
public class BusV2Configuration {

    private static final Logger log = LoggerFactory.getLogger(BusV2Configuration.class);

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
    public CorrelationStore correlationStore(BusV2Properties props) {
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

    @Bean
    public NettyTransportConfig nettyTransportConfig(BusV2Properties props) {
        return new NettyTransportConfig(
                props.heartbeatWriteIdle(),
                props.heartbeatReadIdle(),
                props.connectTimeout(),
                props.maxFrameLength(),
                props.writeBufferHighWaterMark(),
                props.writeBufferLowWaterMark(),
                new ExponentialBackoffWithJitter(),
                new com.evento.transport.codec.JacksonCborCodec(),
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @Bean(destroyMethod = "")
    public TransportServer transportServer(NettyTransportConfig config) {
        return new NettyServerTransport(config);
    }

    @Bean(destroyMethod = "")
    public BusLifecycle busLifecycle(TransportServer transportServer,
                                     ConnectionRegistry connections,
                                     ClusterRegistry cluster,
                                     CorrelationStore correlations,
                                     ForwardingTable forwarding,
                                     BusEventBus eventBus,
                                     PayloadCodec payloadCodec,
                                     BusV2Properties props) {
        return new BusLifecycle(transportServer, connections, cluster, correlations,
                forwarding, eventBus, props.serverInstanceId(),
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), payloadCodec);
    }

    @Bean
    public BusV2Starter busV2Starter(BusLifecycle lifecycle, BusV2Properties props,
                                     javax.sql.DataSource dataSource) {
        // dataSource is injected only to establish a destruction-order edge:
        // Spring must destroy BusV2Starter (→ stop the bus, drain all IO callbacks)
        // before it closes the DataSource / HikariPool.
        return new BusV2Starter(lifecycle, props);
    }

    /**
     * Owns the {@code start/stop} side-effect on {@link BusLifecycle} so the bean
     * itself stays free of {@code @PostConstruct} surprises. Logs the resolved
     * port (useful when {@code port=0} for ephemeral binding in tests).
     */
    public static final class BusV2Starter {
        private final BusLifecycle lifecycle;
        private final BusV2Properties props;

        public BusV2Starter(BusLifecycle lifecycle, BusV2Properties props) {
            this.lifecycle = lifecycle;
            this.props = props;
        }

        @PostConstruct
        public void start() {
            int port = lifecycle.start(props.port());
            log.info("event=bus_v2_started port={} server_instance={}", port, props.serverInstanceId());
            log.info("Evento server ready — accepting bundle registrations on port {}", port);
        }

        @PreDestroy
        public void stop() {
            lifecycle.stop(props.shutdownDeadline());
        }
    }
}
