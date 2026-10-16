package com.evento.spring.boot;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.transport.netty.NettyTransportConfig;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

import java.io.File;
import java.util.List;

/**
 * Builds the bundle from {@link EventoProperties} and the {@link EventoBundleCustomizer}s, and
 * runs it with the application context: started once every bean exists (so components get their
 * collaborators through the injector), stopped gracefully before the beans they use go away.
 *
 * <p>Components (aggregates, projectors, sagas…) are created by Evento, not by Spring; their
 * constructor parameters are resolved from the context by type.
 */
public class EventoBundleLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EventoBundleLifecycle.class);

    /** Late, so the beans components depend on (data sources, web clients) start first and stop after. */
    public static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 1024;

    private final EventoProperties properties;
    private final ListableBeanFactory beanFactory;
    private final ObjectProvider<EventoBundleCustomizer> customizers;
    private final ApplicationEventPublisher events;
    private volatile EventoBundle bundle;

    public EventoBundleLifecycle(EventoProperties properties, ListableBeanFactory beanFactory,
                                 ObjectProvider<EventoBundleCustomizer> customizers,
                                 ApplicationEventPublisher events) {
        this.properties = properties;
        this.beanFactory = beanFactory;
        this.customizers = customizers;
        this.events = events;
    }

    /** The running bundle, or null before {@link #start()} and after {@link #stop()}. */
    public EventoBundle bundle() {
        return bundle;
    }

    /** The running bundle; fails when it is not running. */
    EventoBundle requireBundle() {
        var b = bundle;
        if (b == null) {
            throw new IllegalStateException("the Evento bundle '" + properties.getId() + "' is not running "
                    + "(it starts with the application context, after the beans are created)");
        }
        return b;
    }

    @Override
    public synchronized void start() {
        if (bundle != null) return;
        try {
            var builder = builder();
            for (var customizer : customizers.orderedStream().toList()) {
                customizer.customize(builder);
            }
            // start() returns once connected; the bundle is enabled (routed to) later, after the
            // projectors that wait for the head. That is when it is "started" for the application.
            var hook = builder.getOnEventoStartedHook();
            builder.setOnEventoStartedHook(started -> {
                log.info("event=evento_bundle_started bundle={} servers={}", properties.getId(), properties.getServers());
                events.publishEvent(new EventoBundleStartedEvent(this, started));
                if (hook != null) hook.accept(started);
            });
            bundle = builder.start();
        } catch (Exception e) {
            throw new IllegalStateException("cannot start the Evento bundle '" + properties.getId() + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        var b = bundle;
        if (b == null) return;
        bundle = null;
        b.stop(properties.getShutdownTimeout());
        log.info("event=evento_bundle_stopped bundle={}", properties.getId());
    }

    @Override
    public boolean isRunning() {
        return bundle != null;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isAutoStartup();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    EventoBundle.Builder builder() throws Exception {
        var builder = EventoBundle.Builder.builder()
                .setBundleId(properties.getId())
                .setBundleVersion(properties.getVersion())
                .setBasePackageName(basePackage())
                .setEventoServerMessageBusConfiguration(busConfiguration())
                .setAuthToken(blankToNull(properties.getAuthToken()))
                .setDefaultRequestTimeout(properties.getDefaultRequestTimeout())
                .setRestrictPayloadTypes(properties.getSerialization().isRestrict())
                .setAllowedPayloadPackages(List.copyOf(properties.getSerialization().getAllowedPackages()))
                .setInjector(type -> beanFactory.getBeanProvider(type).getIfAvailable());
        if (properties.getInstanceId() != null) builder.setInstanceId(properties.getInstanceId());
        if (properties.getDescription() != null) builder.setDescription(properties.getDescription());
        if (properties.getZone() != null) builder.setZone(properties.getZone());
        var tls = properties.getTls();
        if (tls.isEnabled()) {
            builder.setTransportConfig(NettyTransportConfig.defaults().withSslContext(clientContext(tls)));
        }
        return builder;
    }

    private String basePackage() {
        if (properties.getBasePackage() != null && !properties.getBasePackage().isBlank()) {
            return properties.getBasePackage();
        }
        if (AutoConfigurationPackages.has(beanFactory)) {
            return AutoConfigurationPackages.get(beanFactory).getFirst();
        }
        throw new IllegalStateException("set evento.bundle.base-package: there is no @SpringBootApplication "
                + "package to default to");
    }

    private EventoServerMessageBusConfiguration busConfiguration() {
        var addresses = properties.getServers().stream().map(EventoBundleLifecycle::address)
                .toArray(ClusterNodeAddress[]::new);
        if (addresses.length == 0) throw new IllegalStateException("evento.bundle.servers is empty");
        var config = new EventoServerMessageBusConfiguration(addresses);
        if (properties.getExpectedClusterName() != null) {
            config = config.withExpectedClusterName(properties.getExpectedClusterName());
        }
        return config;
    }

    static ClusterNodeAddress address(String hostPort) {
        var value = hostPort.trim();
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException("evento.bundle.servers: expected host:port, got '" + hostPort + "'");
        }
        return new ClusterNodeAddress(value.substring(0, colon), Integer.parseInt(value.substring(colon + 1)));
    }

    static SslContext clientContext(EventoProperties.Tls tls) throws Exception {
        var builder = SslContextBuilder.forClient();
        if (tls.getTrustCerts() != null && !tls.getTrustCerts().isBlank()) {
            builder.trustManager(new File(tls.getTrustCerts()));
        }
        boolean chain = tls.getCertChain() != null && !tls.getCertChain().isBlank();
        boolean key = tls.getPrivateKey() != null && !tls.getPrivateKey().isBlank();
        if (chain != key) {
            throw new IllegalStateException("evento.bundle.tls: cert-chain and private-key go together (mutual TLS)");
        }
        if (chain) {
            builder.keyManager(new File(tls.getCertChain()), new File(tls.getPrivateKey()),
                    blankToNull(tls.getPrivateKeyPassword()));
        }
        return builder.build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
