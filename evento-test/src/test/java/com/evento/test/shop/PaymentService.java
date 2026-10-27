package com.evento.test.shop;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;

import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentService {

    /** Stands for the payment provider. */
    public final List<String> charged = new ArrayList<>();

    @CommandHandler
    OrderCharged handle(Charge c) {
        if (c.amount <= 0) throw new IllegalArgumentException("nothing to charge");
        charged.add(c.orderId);
        return new OrderCharged(c.orderId, c.amount);
    }

    @CommandHandler
    void handle(Notify n) {
    }
}
