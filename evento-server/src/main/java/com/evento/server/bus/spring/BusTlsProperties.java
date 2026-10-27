package com.evento.server.bus.spring;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.List;

/**
 * TLS for the bundle bus port ({@code evento.server.bus.tls.*}). Off unless {@code enabled}.
 *
 * <ul>
 *   <li>{@code cert-chain}: PEM file with the server certificate followed by its intermediates;</li>
 *   <li>{@code private-key}: PEM file with the server key, PKCS#8 (convert a PKCS#1
 *       {@code BEGIN RSA PRIVATE KEY} file with {@code openssl pkcs8 -topk8});</li>
 *   <li>{@code private-key-password}: when the key is encrypted;</li>
 *   <li>{@code trust-certs}: PEM file with the CAs that sign bundle certificates; needed for
 *       mutual TLS;</li>
 *   <li>{@code client-auth}: {@code none} (default), {@code optional} or {@code require} (mTLS:
 *       a bundle without a certificate signed by {@code trust-certs} cannot connect);</li>
 *   <li>{@code protocols}: defaults to TLSv1.3 and TLSv1.2.</li>
 * </ul>
 *
 * <p>TLS authenticates the channel; {@code evento.server.bus.auth-token} still decides which
 * bundles may register. Production uses both (see {@link SecurityProfileGuard}).
 */
@ConfigurationProperties(prefix = "evento.server.bus.tls")
public record BusTlsProperties(
        boolean enabled,
        String certChain,
        String privateKey,
        String privateKeyPassword,
        String trustCerts,
        ClientAuth clientAuth,
        List<String> protocols
) {

    public BusTlsProperties {
        if (clientAuth == null) clientAuth = ClientAuth.NONE;
        if (protocols == null || protocols.isEmpty()) protocols = List.of("TLSv1.3", "TLSv1.2");
    }

    /** True when bundles must present a certificate the server trusts. */
    public boolean mutual() {
        return enabled && clientAuth == ClientAuth.REQUIRE;
    }

    /** The server context, or null when TLS is off. Fails fast on a missing or unreadable file. */
    public SslContext serverContext() {
        if (!enabled) return null;
        var chain = requireFile("cert-chain", certChain);
        var key = requireFile("private-key", privateKey);
        if (clientAuth != ClientAuth.NONE && (trustCerts == null || trustCerts.isBlank())) {
            throw new IllegalStateException("evento.server.bus.tls.client-auth=" + clientAuth.name().toLowerCase()
                    + " needs evento.server.bus.tls.trust-certs (the CAs that sign bundle certificates)");
        }
        try {
            var builder = SslContextBuilder.forServer(chain, key,
                            privateKeyPassword == null || privateKeyPassword.isBlank() ? null : privateKeyPassword)
                    .clientAuth(clientAuth)
                    .protocols(protocols);
            if (trustCerts != null && !trustCerts.isBlank()) {
                builder.trustManager(requireFile("trust-certs", trustCerts));
            }
            return builder.build();
        } catch (SSLException | IllegalArgumentException e) {
            throw new IllegalStateException("evento.server.bus.tls: cannot build the TLS context: " + e.getMessage(), e);
        }
    }

    private static File requireFile(String property, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("evento.server.bus.tls.enabled=true needs evento.server.bus.tls." + property);
        }
        var file = new File(path);
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalStateException("evento.server.bus.tls." + property + ": cannot read " + file.getAbsolutePath());
        }
        return file;
    }
}
