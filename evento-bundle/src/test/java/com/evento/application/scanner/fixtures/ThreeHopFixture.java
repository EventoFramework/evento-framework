package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Handler → step1 → step2 → step3 → commandGateway.send — three levels of indirection.
 */
public class ThreeHopFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        step1();
    }

    private void step1() {
        step2();
    }

    private void step2() {
        step3();
    }

    private void step3() {
        commandGateway.send(new SentDomainCmd());
    }
}
