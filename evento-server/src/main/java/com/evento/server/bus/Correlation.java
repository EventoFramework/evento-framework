package com.evento.server.bus;

import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;

import java.util.function.Consumer;

public record Correlation(EventoRequest request, Consumer<EventoResponse> response) {
}
