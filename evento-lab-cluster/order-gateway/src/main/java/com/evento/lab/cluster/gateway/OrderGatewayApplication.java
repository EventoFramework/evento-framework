package com.evento.lab.cluster.gateway;

import com.evento.lab.cluster.gateway.bundle.GatewayBundle;
import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.LabSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Map;

/** Boots bundle {@code order-gateway} for the region named by {@code EVENTO_REGION}. */
@SpringBootApplication
@Import(LabSupportConfiguration.class)
public class OrderGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderGatewayApplication.class, args);
    }

    @Bean
    public BundleDefinition orderGatewayBundle(BundleDeployment deployment) {
        deployment.requireRegion();
        return new BundleDefinition() {
            @Override
            public Class<?> anchor() {
                return GatewayBundle.class;
            }

            @Override
            public Map<Class<?>, Object> injectables() {
                return Map.of();
            }

            @Override
            public boolean usesConsumers() {
                return false;
            }
        };
    }

    @Bean
    public PriceReaders priceReaders(LabDatabases databases, BundleDeployment deployment) {
        return new PriceReaders(databases, deployment.instanceId());
    }
}
