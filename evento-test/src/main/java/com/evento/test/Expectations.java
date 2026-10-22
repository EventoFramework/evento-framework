package com.evento.test;

import com.evento.common.modeling.messaging.payload.Command;

import java.util.List;
import java.util.stream.Collectors;

/** Assertions shared by the fixture results. */
final class Expectations {

    private Expectations() {
    }

    static void sameCommands(List<? extends Command> expected, List<? extends Command> actual) {
        boolean same = expected.size() == actual.size();
        for (int i = 0; same && i < expected.size(); i++) {
            same = Payloads.same(expected.get(i), actual.get(i));
        }
        if (!same) {
            throw new AssertionError("expected commands " + describe(expected) + " but were " + describe(actual));
        }
    }

    static String describe(List<?> payloads) {
        return payloads.stream().map(Payloads::describe).collect(Collectors.joining(", ", "[", "]"));
    }
}
