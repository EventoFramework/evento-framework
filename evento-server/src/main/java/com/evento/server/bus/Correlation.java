package com.evento.server.bus;

import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;

import java.util.function.Consumer;

public record Correlation(NodeAddress from, NodeAddress to,
                          EventoRequest request, Consumer<EventoResponse> response) {
}
