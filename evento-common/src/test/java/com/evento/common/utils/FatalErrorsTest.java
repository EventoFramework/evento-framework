package com.evento.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FatalErrorsTest {

    private String previousHaltProperty;

    @BeforeEach
    void disableHalt() {
        // The halting branch would kill the test JVM; every escalate call in tests
        // must run with halting disabled.
        previousHaltProperty = System.setProperty(FatalErrors.HALT_PROPERTY, "false");
    }

    @AfterEach
    void restoreHaltProperty() {
        if (previousHaltProperty == null) {
            System.clearProperty(FatalErrors.HALT_PROPERTY);
        } else {
            System.setProperty(FatalErrors.HALT_PROPERTY, previousHaltProperty);
        }
    }

    @Test
    void plainOutOfMemoryErrorIsFatal() {
        assertThat(FatalErrors.isFatal(new OutOfMemoryError("Java heap space"))).isTrue();
    }

    @Test
    void wrappedOutOfMemoryErrorIsFatal() {
        // The shape seen in production: repository call wraps the OOM twice.
        var t = new RuntimeException(new IllegalStateException(new OutOfMemoryError("Java heap space")));
        assertThat(FatalErrors.isFatal(t)).isTrue();
    }

    @Test
    void stackOverflowIsFatal() {
        assertThat(FatalErrors.isFatal(new StackOverflowError())).isTrue();
    }

    @Test
    void ordinaryExceptionsAreNotFatal() {
        assertThat(FatalErrors.isFatal(new RuntimeException("boom"))).isFalse();
        assertThat(FatalErrors.isFatal(new Exception(new IllegalArgumentException()))).isFalse();
    }

    @Test
    void cyclicCauseChainTerminates() {
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        a.initCause(b);
        assertThat(FatalErrors.isFatal(a)).isFalse();
    }

    @Test
    void escalateIsNoOpForNonFatal() {
        assertThatCode(() -> FatalErrors.escalateIfFatal(new RuntimeException("boom")))
                .doesNotThrowAnyException();
    }

    @Test
    void escalateWithHaltDisabledReturnsForFatal() {
        // With evento.fatal.halt=false the fatal branch must return instead of halting,
        // which is also what allows this test process to survive.
        assertThatCode(() -> FatalErrors.escalateIfFatal(new OutOfMemoryError("Java heap space")))
                .doesNotThrowAnyException();
    }
}
