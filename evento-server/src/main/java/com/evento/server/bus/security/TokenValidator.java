package com.evento.server.bus.security;

/**
 * Validates the {@code authToken} carried by a bundle's {@code Hello} message.
 * Implementations decide whether the supplied token authorizes the requesting
 * bundle to register against this server.
 *
 * <p>Two built-in helpers are provided:
 * <ul>
 *   <li>{@link #acceptAll()} — disable authentication (development / inside a
 *       trusted network). Default in {@code BusLifecycle}.</li>
 *   <li>{@link #sharedSecret(String)} — accept any bundle that presents
 *       exactly the configured token (constant-time compared).</li>
 * </ul>
 *
 * <p>Production deployments typically plug in a JWT verifier or a lookup
 * against an external bundle registry. The decision is binary (accept/reject)
 * with a free-form reason that surfaces in the {@code Reject} message body.
 */
@FunctionalInterface
public interface TokenValidator {

    sealed interface Decision permits Decision.Accept, Decision.Reject {
        record Accept() implements Decision {}
        record Reject(String reason) implements Decision {}
        Decision ACCEPT = new Accept();
    }

    Decision validate(String bundleId, String instanceId, String token);

    static TokenValidator acceptAll() {
        return (b, i, t) -> Decision.ACCEPT;
    }

    static TokenValidator sharedSecret(String expected) {
        if (expected == null || expected.isEmpty()) {
            throw new IllegalArgumentException("shared secret must be non-empty");
        }
        byte[] expectedBytes = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return (b, i, t) -> {
            if (t == null) return new Decision.Reject("missing token");
            byte[] actualBytes = t.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.security.MessageDigest.isEqual(expectedBytes, actualBytes)
                    ? Decision.ACCEPT
                    : new Decision.Reject("token mismatch");
        };
    }
}
