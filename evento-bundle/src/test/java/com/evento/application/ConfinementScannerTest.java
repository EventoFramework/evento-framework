package com.evento.application;

import com.evento.application.scanner.fixtures.AbstractSendFixture;
import com.evento.application.scanner.fixtures.confinement.CleanHelperFixture;
import com.evento.application.scanner.fixtures.confinement.ComponentServiceFixture;
import com.evento.application.scanner.fixtures.confinement.LeakyHelperFixture;
import com.evento.common.modeling.messaging.payload.Command;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfinementScannerTest {

    // ── single-class scan ──────────────────────────────────────────────────────

    @Test
    void scanFlagsBothGatewayKindsInPlainHelper() throws Exception {
        var violations = ConfinementScanner.scan(LeakyHelperFixture.class);

        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(ConfinementScanner.Violation::kind)
                .containsExactlyInAnyOrder("command", "query");
        assertThat(violations).allSatisfy(v -> {
            assertThat(v.className()).isEqualTo(LeakyHelperFixture.class.getName());
            assertThat(v.line()).isGreaterThan(0);
        });
    }

    @Test
    void scanFindsNothingInCleanHelper() throws Exception {
        assertThat(ConfinementScanner.scan(CleanHelperFixture.class)).isEmpty();
    }

    // ── package sweep ──────────────────────────────────────────────────────────

    @Test
    void checkSkipsComponentClasses() {
        var violations = ConfinementScanner.check(
                List.of(ComponentServiceFixture.class, CleanHelperFixture.class),
                Set.of(ComponentServiceFixture.class));

        assertThat(violations).isEmpty();
    }

    @Test
    void checkFlagsGatewayCallsOutsideComponents() {
        var violations = ConfinementScanner.check(
                List.of(LeakyHelperFixture.class, ComponentServiceFixture.class,
                        CleanHelperFixture.class),
                Set.of(ComponentServiceFixture.class));

        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(ConfinementScanner.Violation::className)
                .containsOnly(LeakyHelperFixture.class.getName());
    }

    // ── unresolved payload types (AsmInvocationScanner integration) ────────────

    @Test
    void abstractBaseTypedSendIsReportedAsUnresolved() throws Exception {
        var result = AsmInvocationScanner.scan(
                AbstractSendFixture.class.getDeclaredMethod("onEvent", Command.class));

        assertThat(result.commands()).isEmpty();
        assertThat(result.unresolved()).hasSize(1);
        assertThat(result.unresolved().values()).containsExactly("command");
    }

    @Test
    void concreteSendIsNotReportedAsUnresolved() throws Exception {
        var result = AsmInvocationScanner.scan(
                com.evento.application.scanner.fixtures.DirectCallFixture.class
                        .getDeclaredMethod("onEvent", String.class));

        assertThat(result.commands()).hasSize(1);
        assertThat(result.unresolved()).isEmpty();
    }
}
