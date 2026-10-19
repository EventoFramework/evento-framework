package com.evento.bench.axon.app;

import com.evento.bench.axon.api.Commands.AddItem;
import com.evento.bench.axon.api.Commands.CreateOrder;
import com.evento.bench.axon.entity.Order;
import com.evento.bench.core.BenchConfig;
import com.evento.bench.core.LoadRunner;
import com.evento.bench.core.OrderCommands;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spring profiles pick the role: {@code handler} registers the {@link Order} entity, {@code load}
 * runs the workload through the {@link CommandGateway}. With the JPA engine both run in one JVM
 * (open-source Axon Framework 5 has no distributed command bus); with Axon Server they are two
 * applications and the commands travel through Axon Server, as they travel through the Evento server.
 */
@SpringBootApplication
public class AxonBench {

    public static void main(String[] args) {
        SpringApplication.run(AxonBench.class, args);
    }

    @Configuration
    @Profile("handler")
    @ComponentScan(basePackageClasses = Order.class)
    static class Handler {
    }

    @Bean
    @Profile("load")
    CommandLineRunner load(CommandGateway gateway) {
        return args -> {
            var commands = new OrderCommands() {
                @Override
                public CompletableFuture<?> create(String orderId) {
                    return gateway.send(new CreateOrder(orderId)).getResultMessage();
                }

                @Override
                public CompletableFuture<?> addItem(String orderId, String sku, int quantity) {
                    return gateway.send(new AddItem(orderId, sku, quantity)).getResultMessage();
                }
            };
            awaitHandler(commands);
            new LoadRunner(BenchConfig.fromEnvironment(), commands).runAll();
            System.exit(0);
        };
    }

    /** Until the handler is registered, a command has nowhere to go. */
    static void awaitHandler(OrderCommands commands) throws InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                commands.create("probe-" + System.nanoTime()).get(5, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                if (attempt > 120) throw new IllegalStateException("no handler for the benchmark commands", e);
                Thread.sleep(1000);
            }
        }
    }
}
