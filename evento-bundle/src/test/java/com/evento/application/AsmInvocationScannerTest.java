package com.evento.application;

import com.evento.application.scanner.fixtures.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AsmInvocationScannerTest {

    // ── helpers ────────────────────────────────────────────────────────────────

    private static Method method(Class<?> cls, String name) throws NoSuchMethodException {
        return cls.getDeclaredMethod(name, String.class);
    }

    // ── direct call ────────────────────────────────────────────────────────────

    @Test
    void detectsDirectCommandInvocation() throws Exception {
        var result = AsmInvocationScanner.scan(method(DirectCallFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries()).isEmpty();
        assertThat(result.handlerLine()).isGreaterThan(0);
    }

    // ── one-hop indirection ────────────────────────────────────────────────────

    @Test
    void detectsCommandInvocationThroughOnePrivateHelper() throws Exception {
        var result = AsmInvocationScanner.scan(method(OneHopFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries()).isEmpty();
    }

    // ── three-hop indirection (the "jump" guarantee) ───────────────────────────

    @Test
    void detectsCommandInvocationThroughThreeLevelsOfIndirection() throws Exception {
        var result = AsmInvocationScanner.scan(method(ThreeHopFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries()).isEmpty();
    }

    // ── query detection ────────────────────────────────────────────────────────

    @Test
    void detectsDirectQueryInvocation() throws Exception {
        var result = AsmInvocationScanner.scan(method(QueryCallerFixture.class, "onEvent"));

        assertThat(result.queries().values()).containsExactly("SentQuery");
        assertThat(result.commands()).isEmpty();
    }

    // ── lambda body ────────────────────────────────────────────────────────────

    @Test
    void detectsCommandInvocationInsideLambdaBody() throws Exception {
        var result = AsmInvocationScanner.scan(method(LambdaCallFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries()).isEmpty();
    }

    // ── ServiceCommand subtype ─────────────────────────────────────────────────

    @Test
    void detectsServiceCommandSubtype() throws Exception {
        var result = AsmInvocationScanner.scan(method(ServiceCmdFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentServiceCmd");
        assertThat(result.queries()).isEmpty();
    }

    // ── isolation between handlers in the same class ───────────────────────────

    @Test
    void handlerADoesNotSeeHandlerBPayloads() throws Exception {
        var result = AsmInvocationScanner.scan(method(MixedHandlersFixture.class, "handlerA"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries().values()).containsExactly("SentQuery");
        assertThat(result.commands().values()).doesNotContain("AnotherDomainCmd");
    }

    @Test
    void handlerBDoesNotSeeHandlerAPayloads() throws Exception {
        var result = AsmInvocationScanner.scan(method(MixedHandlersFixture.class, "handlerB"));

        assertThat(result.commands().values()).containsExactly("AnotherDomainCmd");
        assertThat(result.queries()).isEmpty();
        assertThat(result.commands().values()).doesNotContain("SentDomainCmd");
    }

    // ── no invocations ─────────────────────────────────────────────────────────

    @Test
    void returnsEmptyForHandlerWithNoGatewayCalls() throws Exception {
        var result = AsmInvocationScanner.scan(
                NoInvocationFixture.class.getDeclaredMethod("onEvent", String.class));

        assertThat(result.commands()).isEmpty();
        assertThat(result.queries()).isEmpty();
    }

    // ── stored variable (ASTORE → ALOAD chain) ────────────────────────────────

    @Test
    void detectsCommandStoredInLocalVariableBeforeSend() throws Exception {
        var result = AsmInvocationScanner.scan(method(StoredVariableFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries()).isEmpty();
    }

    // ── factory method (return-type from descriptor) ──────────────────────────

    @Test
    void detectsCommandReturnedByPrivateFactoryMethod() throws Exception {
        var result = AsmInvocationScanner.scan(method(FactoryMethodFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries()).isEmpty();
    }

    // ── no false positive for created-but-not-sent ────────────────────────────

    @Test
    void doesNotReportCommandThatIsCreatedButNeverSentToGateway() throws Exception {
        var result = AsmInvocationScanner.scan(method(NotSentFixture.class, "onEvent"));

        assertThat(result.commands()).isEmpty();
        assertThat(result.queries()).isEmpty();
    }

    // ── handler line number ────────────────────────────────────────────────────

    @Test
    void handlerLineIsPopulated() throws Exception {
        var result = AsmInvocationScanner.scan(method(DirectCallFixture.class, "onEvent"));

        assertThat(result.handlerLine()).isGreaterThan(0);
    }

    // ── invocation line numbers ────────────────────────────────────────────────

    @Test
    void invocationKeyIsSourceLineNumber() throws Exception {
        var result = AsmInvocationScanner.scan(method(DirectCallFixture.class, "onEvent"));

        // The map key must be a positive source line, not a dummy index
        result.commands().forEach((line, name) -> assertThat(line).isGreaterThan(0));
    }

    // ── unknown-ref stack slots (null literal, array ops) ──────────────────────

    @Test
    void scansHandlerContainingNullLiteralAndArrayOps() throws Exception {
        // Regression: these opcodes used to push raw null onto the ArrayDeque
        // stack, aborting the scan with a message-less NPE.
        var result = AsmInvocationScanner.scan(method(UnknownRefStackFixture.class, "onEvent"));

        assertThat(result.commands().values()).containsExactly("SentDomainCmd");
        assertThat(result.queries()).isEmpty();
        assertThat(result.handlerLine()).isGreaterThan(0);
    }
}
