package com.evento.lab.cluster.command;

import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.LabSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Boots bundle {@code order-command}; every deployment detail comes from the environment. */
@SpringBootApplication
@Import(LabSupportConfiguration.class)
public class OrderCommandApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderCommandApplication.class, args);
    }

    @Bean
    public BundleDefinition orderCommandBundle(BundleDeployment deployment, LabDatabases databases) {
        return new OrderCommandBundle(deployment, databases);
    }
}
