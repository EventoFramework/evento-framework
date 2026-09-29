package com.evento.lab.cluster.command.bundle;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.cluster.api.Contexts;
import com.evento.lab.cluster.api.command.SetPriceCommand;
import com.evento.lab.cluster.api.event.PriceChangedEvent;

/**
 * The world-wide price list. Every price change is a cross-region event: it
 * is stamped with the literal context {@link Contexts#GLOBAL} ({@code "ALL"},
 * <b>not</b> the wildcard {@code Context.ALL = "*"}), so no regional consumer
 * receives it and the single global lane of order-query fans it out to every
 * regional database.
 */
@Service
public class PriceListService {

    @CommandHandler
    PriceChangedEvent handle(SetPriceCommand cmd) {
        if (cmd.getSku() == null || cmd.getSku().isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (cmd.getPriceCents() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        var currency = cmd.getCurrency() == null || cmd.getCurrency().isBlank() ? "EUR" : cmd.getCurrency();
        return new PriceChangedEvent(cmd.getSku(), cmd.getPriceCents(), currency, cmd.getOrigin(),
                cmd.getIdempotencyKey())
                .setContext(Contexts.GLOBAL);
    }
}
