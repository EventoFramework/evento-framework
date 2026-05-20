package com.evento.server.bus;

import com.evento.server.bus.v1adapter.MessageBusFacade;
import com.evento.server.bus.v2.BusLifecycleFacade;
import com.evento.server.bus.v2.lifecycle.BusLifecycle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the {@link BusFacade} bean. The v1 adapter is the default
 * so existing deployments keep their behaviour unchanged; the v2 adapter is
 * picked up only when {@code evento.server.bus.v2.enabled=true}, which is also
 * the flag that activates {@code BusV2Configuration} and provisions the
 * {@link BusLifecycle} bean it adapts.
 *
 * <p>Both beans are tagged {@code @Primary} on their conditional branch so the
 * controllers can inject {@code BusFacade} unconditionally. Two facade beans
 * never coexist — the two {@link ConditionalOnProperty} predicates are mutually
 * exclusive (one matches {@code true}, the other matches {@code false} or
 * missing).
 */
@Configuration
public class BusFacadeConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "evento.server.bus.v2", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    static class V1FacadeConfiguration {
        @Bean
        public BusFacade busFacade(MessageBus messageBus) {
            return new MessageBusFacade(messageBus);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "evento.server.bus.v2", name = "enabled", havingValue = "true")
    static class V2FacadeConfiguration {
        @Bean
        public BusFacade busFacade(BusLifecycle busLifecycle) {
            return new BusLifecycleFacade(busLifecycle);
        }
    }
}
