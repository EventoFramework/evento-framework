package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Handler → one private helper → commandGateway.send.
 */
public class OneHopFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        doWork();
    }

    private void doWork() {
        commandGateway.send(new SentDomainCmd());
    }
}
