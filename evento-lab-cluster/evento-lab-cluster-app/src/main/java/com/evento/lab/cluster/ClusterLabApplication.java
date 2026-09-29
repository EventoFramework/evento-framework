package com.evento.lab.cluster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single jar of evento-lab-cluster. {@code EVENTO_ROLE} picks what the JVM
 * is: a cell core (command handlers + global read model), a region gateway
 * (HTTP + regional read model) or a saga/observer host.
 */
@SpringBootApplication
public class ClusterLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClusterLabApplication.class, args);
    }
}
