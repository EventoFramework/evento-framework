package com.evento.lab.cluster.saga;

import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.LabSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Boots bundle {@code order-saga}; its regions come from {@code EVENTO_CONTEXTS}. */
@SpringBootApplication
@Import(LabSupportConfiguration.class)
public class OrderSagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderSagaApplication.class, args);
    }

    @Bean
    public BundleDefinition orderSagaBundle() {
        return new OrderSagaBundle();
    }
}
