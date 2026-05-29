package com.evento.server.bus.handshake;

import com.evento.server.bus.NodeAddress;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.Transport;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Welcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Processes a {@link Hello} from a freshly accepted bundle and replies with
 * {@link Welcome} (on success) or {@link Reject} (on protocol mismatch /
 * duplicate instance / internal error). The handler is *stateless*: it depends
 * on injected predicates for the validity decisions, so policy lives in the
 * lifecycle layer rather than here.
 */
public final class HandshakeHandler {

    private static final Logger log = LoggerFactory.getLogger(HandshakeHandler.class);

    public sealed interface HandshakeOutcome
            permits HandshakeOutcome.Accepted, HandshakeOutcome.Rejected {
        record Accepted(NodeAddress address, BundleVersionInfo info) implements HandshakeOutcome {}
        record Rejected(String code, String reason) implements HandshakeOutcome {}
    }

    public record BundleVersionInfo(String bundleId, String instanceId, String bundleVersion) {}

    private final String serverInstanceId;
    private final Set<String> serverCapabilities;
    private final BiFunction<Hello, BundleVersionInfo, HandshakeOutcome> validator;

    public HandshakeHandler(String serverInstanceId,
                            Set<String> serverCapabilities,
                            BiFunction<Hello, BundleVersionInfo, HandshakeOutcome> validator) {
        this.serverInstanceId = serverInstanceId;
        this.serverCapabilities = Set.copyOf(serverCapabilities);
        this.validator = validator;
    }

    /**
     * Process the {@link Hello}, send the appropriate reply on the transport, and
     * return the outcome. The returned future completes after the reply has been
     * accepted by the underlying I/O so the caller can chain
     * post-handshake setup or close on rejection.
     */
    public CompletableFuture<HandshakeOutcome> handle(Hello hello, Transport transport) {
        if (hello.protocolVersion() != HandshakeProtocol.PROTOCOL_VERSION) {
            return sendReject(hello, transport, Reject.CODE_PROTOCOL_VERSION,
                    "server speaks v" + HandshakeProtocol.PROTOCOL_VERSION
                            + ", client offered v" + hello.protocolVersion());
        }
        var info = new BundleVersionInfo(hello.bundleId(), hello.instanceId(), hello.bundleVersion());
        var verdict = validator.apply(hello, info);
        if (verdict instanceof HandshakeOutcome.Rejected(String code, String reason)) {
            return sendReject(hello, transport, code, reason);
        }
        var accepted = (HandshakeOutcome.Accepted) verdict;
        var acceptedCaps = intersection(hello.capabilities(), serverCapabilities);
        var welcome = new Welcome(hello.correlationId(), HandshakeProtocol.PROTOCOL_VERSION,
                serverInstanceId, acceptedCaps, System.currentTimeMillis());
        return transport.send(welcome)
                .thenApply(v -> {
                    log.info("event=handshake_accepted bundle={} instance={} version={} caps={}",
                            hello.bundleId(), hello.instanceId(), hello.bundleVersion(), acceptedCaps);
                    return (HandshakeOutcome) accepted;
                });
    }

    private CompletableFuture<HandshakeOutcome> sendReject(Hello hello, Transport transport,
                                                            String code, String reason) {
        var reject = new Reject(hello.correlationId(), code, reason, System.currentTimeMillis());
        log.warn("event=handshake_rejected bundle={} instance={} code={} reason={}",
                hello.bundleId(), hello.instanceId(), code, reason);
        return transport.send(reject)
                .thenApply(v -> (HandshakeOutcome) new HandshakeOutcome.Rejected(code, reason));
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        if (a == null || a.isEmpty()) return Set.of();
        return a.stream().filter(b::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
