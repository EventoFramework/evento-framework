package com.evento.lab.ms.command.service;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.ms.api.command.OpenPaymentIntentCommand;
import com.evento.lab.ms.api.event.PaymentIntentOpenedEvent;

@Service
public class PaymentService {

    @CommandHandler
    PaymentIntentOpenedEvent handle(OpenPaymentIntentCommand cmd) {
        return new PaymentIntentOpenedEvent(cmd.getOrderId(), cmd.getPaymentIntentId());
    }
}
