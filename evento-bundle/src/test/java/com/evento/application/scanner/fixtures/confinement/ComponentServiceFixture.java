package com.evento.application.scanner.fixtures.confinement;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Service;

/**
 * A component class ({@code @Service}) that invokes the gateway — legitimate:
 * the confinement check must skip registered component classes.
 */
@Service
public class ComponentServiceFixture {

    private CommandGateway commandGateway;

    public void delegate() {
        commandGateway.send(new SentDomainCmd());
    }
}
