package com.evento.application.scanner.fixtures;

/**
 * Handler that never calls any gateway — result must be empty sets.
 */
public class NoInvocationFixture {

    public String onEvent(String event) {
        return "handled: " + event;
    }
}
