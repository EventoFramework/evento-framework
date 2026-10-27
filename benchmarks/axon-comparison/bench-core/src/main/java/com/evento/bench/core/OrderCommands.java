package com.evento.bench.core;

import java.util.concurrent.CompletableFuture;

/**
 * The two commands of the benchmark domain, as each system sends them. A future completes when
 * the command's event is stored (the caller's reply), and fails when the command was refused.
 */
public interface OrderCommands {

    /** Creates order {@code orderId}: one {@code OrderCreated} event; refused if it exists. */
    CompletableFuture<?> create(String orderId);

    /** Adds an item to an existing order: one {@code ItemAdded} event; refused if it does not exist. */
    CompletableFuture<?> addItem(String orderId, String sku, int quantity);
}
