package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Command produced by a private factory method (return type is the concrete type).
 * Tests that the scanner resolves the return-type descriptor of intra-class calls.
 */
public class FactoryMethodFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        commandGateway.send(buildCmd());
    }

    private SentDomainCmd buildCmd() {
        return new SentDomainCmd();
    }
}
