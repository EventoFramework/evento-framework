package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;

/**
 * A command is instantiated but NEVER passed to any gateway.
 * Verifies that the scanner does not produce false positives for unused instances.
 */
public class NotSentFixture {

    public void onEvent(String event) {
        SentDomainCmd cmd = new SentDomainCmd();
        System.out.println(cmd);   // used, but not via a gateway
    }
}
