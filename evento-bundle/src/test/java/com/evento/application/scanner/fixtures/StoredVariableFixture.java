package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Command stored in a local variable before being passed to the gateway.
 * Tests that the scanner follows ASTORE→ALOAD chains.
 */
public class StoredVariableFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        SentDomainCmd cmd = new SentDomainCmd();
        commandGateway.send(cmd);
    }
}
