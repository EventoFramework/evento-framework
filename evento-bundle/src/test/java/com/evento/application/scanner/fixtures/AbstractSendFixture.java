package com.evento.application.scanner.fixtures;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.messaging.payload.Command;

/**
 * Handler sends a value typed as the abstract {@link Command} base — the
 * concrete payload type is statically unresolvable, so the scan must report
 * the call in {@code Result.unresolved()} rather than dropping it silently.
 */
public class AbstractSendFixture {

    private CommandGateway commandGateway;

    public void onEvent(Command cmd) {
        commandGateway.send(cmd);
    }
}
