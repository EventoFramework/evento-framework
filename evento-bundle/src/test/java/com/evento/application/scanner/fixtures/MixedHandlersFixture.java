package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.AnotherDomainCmd;
import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.application.scanner.fixtures.payloads.SentQuery;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;

/**
 * Two independent handler methods, each with different invocations.
 * Verifies that scanning one handler does not bleed into the other.
 */
public class MixedHandlersFixture {

    private CommandGateway commandGateway;
    private QueryGateway queryGateway;

    /** Sends SentDomainCmd and queries SentQuery — no AnotherDomainCmd. */
    public void handlerA(String event) {
        commandGateway.send(new SentDomainCmd());
        queryGateway.query(new SentQuery());
    }

    /** Sends AnotherDomainCmd — no SentDomainCmd or SentQuery. */
    public void handlerB(String event) {
        commandGateway.send(new AnotherDomainCmd());
    }
}
