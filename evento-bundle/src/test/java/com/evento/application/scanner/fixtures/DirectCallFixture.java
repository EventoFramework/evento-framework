package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Handler directly calls commandGateway.send — no indirection.
 */
public class DirectCallFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        commandGateway.send(new SentDomainCmd());
    }
}
