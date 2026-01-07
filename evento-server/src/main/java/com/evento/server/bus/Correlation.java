package com.evento.server.bus;

import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.function.Consumer;

import java.util.Objects;
import java.util.function.Consumer;

@Getter
@RequiredArgsConstructor
public final class Correlation {

    private final NodeAddress from;
    private final NodeAddress to;
    private final EventoRequest request;
    private final Consumer<EventoResponse> response;

}

