package com.evento.server.bus.admin;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.messaging.message.internal.EventoMessage;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.common.performance.PerformanceInvocationsMessage;
import com.evento.common.performance.PerformanceServiceTimeMessage;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.service.discovery.ConsumerService;
import com.evento.server.service.performance.PerformanceStoreService;
import com.evento.transport.protocol.ProtocolPayloadTypes;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Server-side counterpart of {@code EventoServerAdapter.send(Serializable)}.
 * Subscribes to {@link BusEvent.AdminNotification} and, when the payloadType
 * matches {@link ProtocolPayloadTypes#BUNDLE_ADMIN_NOTIFICATION}, decodes the
 * inner v1 {@link EventoMessage} and dispatches its body to the existing
 * services — same shape v1 {@code MessageBus.handleMessage} used.
 *
 * <p>Three body types matter on this channel (the rest are silently ignored —
 * autoscale signals were removed):
 *
 * <ul>
 *   <li>{@link PerformanceInvocationsMessage} → {@link PerformanceStoreService#saveInvocationsPerformance}</li>
 *   <li>{@link PerformanceServiceTimeMessage} → {@link PerformanceStoreService#saveServiceTimePerformance}</li>
 *   <li>{@link BundleConsumerRegistrationMessage} → {@link ConsumerService#registerConsumers}</li>
 * </ul>
 *
 * <p>Active only when {@code evento.server.bus.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "evento.server.bus.v2", name = "enabled", havingValue = "true")
@ConditionalOnBean(BusLifecycle.class)
public class BundleAdminNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BundleAdminNotificationListener.class);

    private final BusLifecycle lifecycle;
    private final PerformanceStoreService performanceStoreService;
    private final ConsumerService consumerService;
    private final AdminPayloadCodec codec;

    public BundleAdminNotificationListener(BusLifecycle lifecycle,
                                           PerformanceStoreService performanceStoreService,
                                           ConsumerService consumerService) {
        this.lifecycle = lifecycle;
        this.performanceStoreService = performanceStoreService;
        this.consumerService = consumerService;
        this.codec = new AdminPayloadCodec();
    }

    @PostConstruct
    public void subscribe() {
        lifecycle.subscribe(event -> {
            if (!(event instanceof BusEvent.AdminNotification an)) return;
            if (!ProtocolPayloadTypes.BUNDLE_ADMIN_NOTIFICATION.equals(an.payloadType())) return;
            dispatch(an);
        });
    }

    private void dispatch(BusEvent.AdminNotification an) {
        EventoMessage envelope;
        try {
            envelope = codec.decodeMessage(an.payload());
        } catch (RuntimeException decodeError) {
            log.warn("event=admin_notification_decode_failed source={}", an.source().instanceId(), decodeError);
            return;
        }
        var body = envelope.getBody();
        switch (body) {
            case PerformanceInvocationsMessage im -> performanceStoreService.saveInvocationsPerformance(
                    im.getBundle(), im.getInstanceId(), im.getComponent(),
                    im.getAction(), im.getInvocations());
            case PerformanceServiceTimeMessage tm -> performanceStoreService.saveServiceTimePerformance(
                    tm.getBundle(), tm.getInstanceId(), tm.getComponent(),
                    tm.getAction(), tm.getStart(), tm.getEnd());
            case BundleConsumerRegistrationMessage cr -> consumerService.registerConsumers(
                    envelope.getSourceBundleId(), envelope.getSourceInstanceId(),
                    envelope.getSourceBundleVersion(), cr);
            case null -> log.debug("event=admin_notification_empty source={}", an.source().instanceId());
            default -> log.debug("event=admin_notification_unhandled source={} bodyType={}",
                    an.source().instanceId(), body.getClass().getName());
        }
    }
}
