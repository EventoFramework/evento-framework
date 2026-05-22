package com.evento.lab.bundle.command;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.api.command.CancelOrderCommand;
import com.evento.lab.api.command.ConfirmOrderCommand;
import com.evento.lab.api.event.OrderCancelledEvent;
import com.evento.lab.api.event.OrderConfirmedEvent;
import com.evento.lab.api.view.OrderView;

@Service
public class LabService {

    @CommandHandler
    OrderConfirmedEvent handle(ConfirmOrderCommand cmd) {
        var view = new OrderView(cmd.getOrderId(), "", 0, "CONFIRMED", false);
        LabStore.put(view);
        return new OrderConfirmedEvent(cmd.getOrderId());
    }

    @CommandHandler
    OrderCancelledEvent handle(CancelOrderCommand cmd) {
        var view = new OrderView(cmd.getOrderId(), "", 0, "CANCELLED", true);
        LabStore.put(view);
        return new OrderCancelledEvent(cmd.getOrderId(), "cancelled");
    }
}
