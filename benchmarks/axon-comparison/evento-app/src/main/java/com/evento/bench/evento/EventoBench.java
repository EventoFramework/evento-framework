package com.evento.bench.evento;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.bench.core.BenchConfig;
import com.evento.bench.core.LoadRunner;
import com.evento.bench.core.OrderCommands;
import com.evento.bench.evento.aggregate.OrderAggregate;
import com.evento.bench.evento.api.AddItem;
import com.evento.bench.evento.api.CreateOrder;
import com.evento.bench.evento.load.LoadMarker;
import com.evento.common.messaging.gateway.CommandGateway;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * BENCH_ROLE=handler: the bundle that owns {@link OrderAggregate}. BENCH_ROLE=load: a bundle
 * with no components that sends the commands through the server, the path of any caller.
 * EVENTO_HOST / EVENTO_PORT: the bus of the server.
 */
public final class EventoBench {

    public static void main(String[] args) throws Exception {
        String role = System.getenv().getOrDefault("BENCH_ROLE", "load");
        String host = System.getenv().getOrDefault("EVENTO_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("EVENTO_PORT", "3030"));
        boolean handler = role.equals("handler");
        var bundle = EventoBundle.Builder.builder()
                .setBasePackage(handler ? OrderAggregate.class.getPackage() : LoadMarker.class.getPackage())
                .setBundleId(handler ? "bench-orders" : "bench-load")
                .setBundleVersion(1)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress(host, port)))
                .start();
        if (handler) {
            System.out.println("event=bench_handler_ready");
            Thread.currentThread().join();
            return;
        }
        CommandGateway gateway = bundle.getCommandGateway();
        var commands = new OrderCommands() {
            @Override
            public CompletableFuture<?> create(String orderId) {
                return gateway.send(new CreateOrder(orderId));
            }

            @Override
            public CompletableFuture<?> addItem(String orderId, String sku, int quantity) {
                return gateway.send(new AddItem(orderId, sku, quantity));
            }
        };
        awaitHandler(commands);
        new LoadRunner(BenchConfig.fromEnvironment(), commands).runAll();
        System.exit(0);
    }

    /** Until the handler bundle is registered, a command has nowhere to go. */
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
