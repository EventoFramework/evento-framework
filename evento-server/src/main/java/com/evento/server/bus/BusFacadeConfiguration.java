package com.evento.server.bus;

import com.evento.server.bus.BusLifecycleFacade;
import com.evento.server.bus.lifecycle.BusLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusFacadeConfiguration {

    @Bean
    public BusFacade busFacade(BusLifecycle busLifecycle) {
        return new BusLifecycleFacade(busLifecycle);
    }
}
