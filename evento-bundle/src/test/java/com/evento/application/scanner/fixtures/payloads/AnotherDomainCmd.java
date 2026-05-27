package com.evento.application.scanner.fixtures.payloads;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class AnotherDomainCmd extends DomainCommand {
    @Override public String getAggregateId() { return "other-id"; }
}
