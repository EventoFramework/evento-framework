package com.evento.spring.boot;

import com.evento.application.client.BundleClientState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;

/**
 * Ties the application's readiness to the bundle's connection ({@code
 * evento.bundle.connection-readiness=true}): {@code REFUSING_TRAFFIC} while the bundle reconnects,
 * {@code ACCEPTING_TRAFFIC} when it is {@code READY} again. Off by default, following Spring Boot's
 * advice against making readiness depend on an external system; turn it on when the application
 * can serve nothing without Evento, so the orchestrator routes to instances that can.
 */
public class EventoBundleReadiness implements ApplicationListener<EventoBundleStartedEvent> {

    private static final Logger log = LoggerFactory.getLogger(EventoBundleReadiness.class);

    private final ApplicationEventPublisher publisher;

    public EventoBundleReadiness(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onApplicationEvent(EventoBundleStartedEvent event) {
        event.getBundle().onConnectionStateChange((previous, next) -> {
            if (next == BundleClientState.READY) {
                publish(ReadinessState.ACCEPTING_TRAFFIC, next);
            } else if (previous == BundleClientState.READY) {
                publish(ReadinessState.REFUSING_TRAFFIC, next);
            }
        });
    }

    private void publish(ReadinessState state, BundleClientState connection) {
        log.info("event=evento_readiness readiness={} connection={}", state, connection);
        AvailabilityChangeEvent.publish(publisher, this, state);
    }
}
