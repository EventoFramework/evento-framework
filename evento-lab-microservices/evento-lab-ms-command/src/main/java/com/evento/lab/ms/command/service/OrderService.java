package com.evento.lab.ms.command.service;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.ms.api.command.CancelOrderCommand;
import com.evento.lab.ms.api.command.CompleteOrderCommand;
import com.evento.lab.ms.api.command.ConfirmOrderCommand;
import com.evento.lab.ms.api.event.OrderCancelledEvent;
import com.evento.lab.ms.api.event.OrderCompletedEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;

@Service
public class OrderService {

    @CommandHandler
    OrderConfirmedEvent handle(ConfirmOrderCommand cmd) {
        return new OrderConfirmedEvent(cmd.getOrderId());
    }

    @CommandHandler
    OrderCancelledEvent handle(CancelOrderCommand cmd) {
        return new OrderCancelledEvent(cmd.getOrderId(), "cancelled");
    }

    @CommandHandler
    OrderCompletedEvent handle(CompleteOrderCommand cmd) {
        return new OrderCompletedEvent(cmd.getOrderId());
    }
}
