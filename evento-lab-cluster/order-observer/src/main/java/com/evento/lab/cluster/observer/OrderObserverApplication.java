package com.evento.lab.cluster.observer;

import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.LabSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Boots bundle {@code order-observer}; its contexts come from {@code EVENTO_CONTEXTS}. */
@SpringBootApplication
@Import(LabSupportConfiguration.class)
public class OrderObserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderObserverApplication.class, args);
    }

    @Bean
    public BundleDefinition orderObserverBundle(BundleDeployment deployment, LabDatabases databases) {
        return new OrderObserverBundle(deployment, databases);
    }
}
