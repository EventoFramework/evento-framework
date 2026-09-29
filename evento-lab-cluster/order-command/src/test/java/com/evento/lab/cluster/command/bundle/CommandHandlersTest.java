package com.evento.lab.cluster.command.bundle;

import com.evento.common.utils.Context;
import com.evento.lab.cluster.api.Contexts;
import com.evento.lab.cluster.api.command.AddOrderItemCommand;
import com.evento.lab.cluster.api.command.CreateOrderCommand;
import com.evento.lab.cluster.api.command.SetPriceCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The context each command handler stamps on its event: that is what the consumers are routed by. */
class CommandHandlersTest {

    @Test
    void priceChangesAreStampedWithTheLiteralGlobalContextNotTheWildcard() {
        var e = new PriceListService().handle(new SetPriceCommand("sku-1", 1999, null, "EUROPA", "k"));
        assertThat(e.getContext()).isEqualTo(Contexts.GLOBAL).isEqualTo("ALL").isNotEqualTo(Context.ALL);
        assertThat(e.getCurrency()).isEqualTo("EUR");
        assertThat(e.getOrigin()).isEqualTo("EUROPA");
        assertThat(new SetPriceCommand("sku-1", 1, "EUR", null, null).getLockId()).isEqualTo("price-sku-1");
        assertThatThrownBy(() -> new PriceListService().handle(new SetPriceCommand(" ", 1, "EUR", null, null)))
                .hasMessageContaining("sku");
    }

    @Test
    void orderEventsCarryTheOrdersRegionEvenWhenAnotherRegionSendsTheCommand() {
        var aggregate = new OrderAggregate();
        var created = aggregate.handle(new CreateOrderCommand("o1", "CINA", null), null);
        assertThat(created.getContext()).isEqualTo("CINA");
        var state = aggregate.on(created, null);
        var item = aggregate.handle(new AddOrderItemCommand("o1", "sku", 1, "EUROPA", null), state);
        assertThat(item.getContext()).isEqualTo("CINA");
    }
}
