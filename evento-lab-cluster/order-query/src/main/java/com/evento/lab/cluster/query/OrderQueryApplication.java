package com.evento.lab.cluster.query;

import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.LabSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Boots bundle {@code order-query}; which consumers run comes from {@code EVENTO_CONTEXTS}. */
@SpringBootApplication
@Import(LabSupportConfiguration.class)
public class OrderQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderQueryApplication.class, args);
    }

    @Bean
    public BundleDefinition orderQueryBundle(BundleDeployment deployment, LabDatabases databases) {
        return new OrderQueryBundle(deployment, databases);
    }
}
