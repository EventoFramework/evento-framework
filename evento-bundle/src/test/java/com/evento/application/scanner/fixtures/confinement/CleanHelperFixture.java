package com.evento.application.scanner.fixtures.confinement;

/**
 * A plain helper with no gateway interaction — must produce no violations.
 */
public class CleanHelperFixture {

    public String format(String input) {
        return input.trim().toLowerCase();
    }
}
