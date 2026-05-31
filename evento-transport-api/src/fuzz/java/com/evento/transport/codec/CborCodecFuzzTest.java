package com.evento.transport.codec;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.evento.transport.message.Message;

/**
 * Coverage-guided fuzzing of the CBOR wire codec — the single most exposed parser in the
 * framework. Every byte that arrives on the TCP socket reaches {@link Codec#decode} before any
 * authentication or type check, so its hardening matters most.
 *
 * <p>The contract under test: for <em>any</em> input, {@link JacksonCborCodec#decode} must either
 * return an allow-listed {@link Message} or throw {@link CodecException}. It must never leak an
 * undeclared exception (the symptom of an unhandled parser edge case), and any object it returns
 * must be inside the {@link MessageTypeRegistry} whitelist — the gadget-chain defense documented in
 * the security model.
 *
 * <p>Run modes:
 * <ul>
 *   <li><b>Regression (default, CI):</b> {@code ./gradlew :evento-transport-api:test} replays the
 *       checked-in seed corpus and any crash reproducers through the target as ordinary JUnit
 *       invocations — fast, deterministic, no native driver.</li>
 *   <li><b>Fuzzing:</b> {@code JAZZER_FUZZ=1 ./gradlew :evento-transport-api:test} engages libFuzzer
 *       to mutate inputs and chase new coverage. Intended for OSS-Fuzz / a scheduled job, not the
 *       per-PR build.</li>
 * </ul>
 */
class CborCodecFuzzTest {

    private final JacksonCborCodec codec = new JacksonCborCodec();

    /**
     * Decode arbitrary bytes. Any non-{@link CodecException} throwable is a finding.
     */
    @FuzzTest(maxDuration = "120s")
    void decodeNeverLeaksUndeclaredException(FuzzedDataProvider data) {
        byte[] frame = data.consumeRemainingAsBytes();
        try {
            Message decoded = codec.decode(frame);
            // A successful decode must yield a whitelisted type — never a gadget class.
            if (!MessageTypeRegistry.isAllowed(decoded.getClass())) {
                throw new AssertionError("decode returned non-whitelisted type: " + decoded.getClass());
            }
        } catch (CodecException expected) {
            // The only sanctioned failure mode for hostile input.
        }
    }

    /**
     * Offset/length window variant: a malicious or buggy framer can hand the codec a sub-range of a
     * larger buffer. The same contract must hold for every valid (offset, length) window.
     */
    @FuzzTest(maxDuration = "120s")
    void decodeWithOffsetWindowNeverLeaksUndeclaredException(FuzzedDataProvider data) {
        byte[] buf = data.consumeBytes(data.consumeInt(0, 4096));
        if (buf.length == 0) {
            return;
        }
        int offset = data.consumeInt(0, buf.length - 1);
        int length = data.consumeInt(0, buf.length - offset);
        try {
            Message decoded = codec.decode(buf, offset, length);
            if (!MessageTypeRegistry.isAllowed(decoded.getClass())) {
                throw new AssertionError("decode returned non-whitelisted type: " + decoded.getClass());
            }
        } catch (CodecException expected) {
            // Expected for malformed windows.
        }
    }
}
