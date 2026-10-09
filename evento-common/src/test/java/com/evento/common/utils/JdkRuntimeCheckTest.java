package com.evento.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkRuntimeCheckTest {

    private static boolean warns(String version) {
        return JdkRuntimeCheck.warning(Runtime.Version.parse(version)).isPresent();
    }

    @Test
    void theBuildsWithTheTimerHeapBugAreFlagged() {
        assertTrue(warns("25+36"));
        assertTrue(warns("25.0.1+8-27"));
        assertTrue(warns("25.0.2+10"));
    }

    @Test
    void fixedAndUnaffectedBuildsAreNot() {
        assertFalse(warns("25.0.3+9"));
        assertFalse(warns("25.0.4.1+1-LTS"));
        assertFalse(warns("24.0.2+12"));
        assertFalse(warns("21.0.12+7"));
        assertFalse(warns("26+10"));
    }
}
