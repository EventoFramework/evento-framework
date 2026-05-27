package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentServiceCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Handler sends a ServiceCommand — verifies that ServiceCommand subclasses are detected.
 */
public class ServiceCmdFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        commandGateway.send(new SentServiceCmd());
    }
}
