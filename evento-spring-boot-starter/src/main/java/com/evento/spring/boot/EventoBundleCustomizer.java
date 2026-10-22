package com.evento.spring.boot;

import com.evento.application.EventoBundle;

/**
 * A bean that adjusts the {@link EventoBundle.Builder} the starter prepares from
 * {@link EventoProperties}, before the bundle starts: consumer executors, component contexts,
 * upcasters, an interceptor, anything the properties do not cover. Customizers run in
 * {@code @Order}; the starter's own ones run first, so an application customizer can override
 * what they set.
 */
@FunctionalInterface
public interface EventoBundleCustomizer {

    void customize(EventoBundle.Builder builder) throws Exception;
}
