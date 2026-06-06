package com.evento.application.scanner.fixtures.confinement;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.application.scanner.fixtures.payloads.SentQuery;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;

/**
 * A plain (non-component) helper class that invokes both gateways — the
 * confinement violation the scanner must flag: a component delegating here
 * would have these sends invisible to static extraction.
 */
public class LeakyHelperFixture {

    private CommandGateway commandGateway;
    private QueryGateway queryGateway;

    public void fireCommand() {
        commandGateway.send(new SentDomainCmd());
    }

    public void fireQuery() {
        queryGateway.query(new SentQuery());
    }
}
