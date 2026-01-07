package com.evento.server.bus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@RequiredArgsConstructor
public class NodeAddressLeave {
    private final Throwable reason;
    private final long timestamp;
}
