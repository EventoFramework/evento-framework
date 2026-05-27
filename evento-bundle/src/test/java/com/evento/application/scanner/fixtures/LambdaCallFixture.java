package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Handler sends a command inside a lambda body — verifies INVOKEDYNAMIC tracing.
 */
public class LambdaCallFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        Runnable r = () -> commandGateway.send(new SentDomainCmd());
        r.run();
    }
}
