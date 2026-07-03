package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.common.messaging.gateway.CommandGateway;

/**
 * Regression fixture for the ArrayDeque-null crash: each construct below
 * compiles to an opcode that puts an unknown reference on the scanner's
 * abstract stack (ACONST_NULL, ANEWARRAY, AALOAD). Before the UNKNOWN
 * sentinel these pushed raw {@code null}, which {@link java.util.ArrayDeque}
 * rejects with a message-less NPE — killing the scan for any handler that
 * merely contained a null literal. The gateway call must still be detected.
 */
public class UnknownRefStackFixture {

    private CommandGateway commandGateway;

    public void onEvent(String event) {
        String reason = null;                  // ACONST_NULL
        if (event.isEmpty()) reason = "empty";
        Object[] holder = new Object[]{event}; // ANEWARRAY + AASTORE
        Object first = holder[0];              // AALOAD
        System.out.println(reason + " " + first);
        commandGateway.send(new SentDomainCmd());
    }
}
