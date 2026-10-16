package com.evento.spring.boot;

import com.evento.application.EventoBundle;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link EventoBundleLifecycle} once the bundle is enabled on the server (requests are
 * routed to it), which can be after the context has started: projectors annotated
 * {@code waitForHeadReached} delay it until they catch up.
 */
public class EventoBundleStartedEvent extends ApplicationEvent {

    private final transient EventoBundle bundle;

    public EventoBundleStartedEvent(Object source, EventoBundle bundle) {
        super(source);
        this.bundle = bundle;
    }

    public EventoBundle getBundle() {
        return bundle;
    }
}
