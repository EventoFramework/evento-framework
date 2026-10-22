package com.evento.server.bus.spring;

import com.evento.application.client.BundleClient;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code evento.server.bus.tls.*} over real TCP: PEM files written to disk, the context built by
 * {@link BusTlsProperties#serverContext()} exactly as {@link BusConfiguration} does, and bundles
 * connecting with and without a client certificate.
 */
@Timeout(30)
class BusTlsPropertiesIT {

    private static final String TOKEN = "tls-test-token";

    private static X509Bundle ca;
    private static X509Bundle server;
    private static X509Bundle bundle;
    private static X509Bundle stranger;

    private BusLifecycle lifecycle;

    @BeforeAll
    static void issueCertificates() throws Exception {
        ca = new CertificateBuilder().subject("CN=evento-test-ca").setIsCertificateAuthority(true).buildSelfSigned();
        server = new CertificateBuilder().subject("CN=localhost").addSanDnsName("localhost")
                .addExtendedKeyUsageServerAuth().buildIssuedBy(ca);
        bundle = new CertificateBuilder().subject("CN=order-command").addExtendedKeyUsageClientAuth().buildIssuedBy(ca);
        var otherCa = new CertificateBuilder().subject("CN=other-ca").setIsCertificateAuthority(true).buildSelfSigned();
        stranger = new CertificateBuilder().subject("CN=stranger").addExtendedKeyUsageClientAuth().buildIssuedBy(otherCa);
    }

    @AfterEach
    void stop() {
        if (lifecycle != null) lifecycle.stop(Duration.ofMillis(200));
    }

    @Test
    void mutualTlsAcceptsABundleWithACertificateFromTheTrustedCa() throws Exception {
        int port = start(properties(ClientAuth.REQUIRE));
        try (var client = client(port, clientContext(bundle))) {
            client.start().get(10, TimeUnit.SECONDS);
            var response = client.request("com.tls.echo", "hi".getBytes()).get(5, TimeUnit.SECONDS);
            assertThat(new String(response.payload())).isEqualTo("echo:hi");
        }
    }

    @Test
    void mutualTlsRefusesABundleWithoutACertificate() throws Exception {
        int port = start(properties(ClientAuth.REQUIRE));
        try (var client = client(port, clientContext(null))) {
            assertThatThrownBy(() -> client.start().get(3, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void mutualTlsRefusesACertificateFromAnotherCa() throws Exception {
        int port = start(properties(ClientAuth.REQUIRE));
        try (var client = client(port, clientContext(stranger))) {
            assertThatThrownBy(() -> client.start().get(3, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void serverOnlyTlsAcceptsABundleWithoutACertificate() throws Exception {
        int port = start(properties(ClientAuth.NONE));
        try (var client = client(port, clientContext(null))) {
            client.start().get(10, TimeUnit.SECONDS);
            var response = client.request("com.tls.echo", "x".getBytes()).get(5, TimeUnit.SECONDS);
            assertThat(new String(response.payload())).isEqualTo("echo:x");
        }
    }

    @Test
    void disabledTlsBuildsNoContext() {
        assertThat(new BusTlsProperties(false, null, null, null, null, null, null).serverContext()).isNull();
    }

    @Test
    void enabledTlsWithoutFilesFailsFast() {
        assertThatThrownBy(() -> new BusTlsProperties(true, null, null, null, null, null, null).serverContext())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evento.server.bus.tls.cert-chain");
    }

    @Test
    void requiredClientAuthWithoutTrustCertsFailsFast() throws Exception {
        var tls = new BusTlsProperties(true, server.toTempCertChainPem().getPath(),
                server.toTempPrivateKeyPem().getPath(), null, null, ClientAuth.REQUIRE, null);
        assertThatThrownBy(tls::serverContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trust-certs");
    }

    // ----------------------------------------------------------------------

    private static BusTlsProperties properties(ClientAuth clientAuth) throws Exception {
        return new BusTlsProperties(true,
                server.toTempCertChainPem().getPath(),
                server.toTempPrivateKeyPem().getPath(),
                null,
                ca.toTempRootCertPem().getPath(),
                clientAuth,
                null);
    }

    private int start(BusTlsProperties tls) {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var transport = new NettyServerTransport(NettyTransportConfig.defaults().withSslContext(tls.serverContext()));
        lifecycle = new BusLifecycle(transport, connections, new ClusterRegistry(connections),
                new CorrelationStore(Duration.ofMillis(100)), new ForwardingTable(), eventBus,
                "broker-tls", Set.of(), new JacksonCborPayloadCodec(), TokenValidator.sharedSecret(TOKEN),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        return lifecycle.start(0);
    }

    private static SslContext clientContext(X509Bundle identity) throws Exception {
        var builder = SslContextBuilder.forClient().trustManager(ca.toTempRootCertPem());
        if (identity != null) {
            builder.keyManager(identity.toTempCertChainPem(), identity.toTempPrivateKeyPem());
        }
        return builder.build();
    }

    private static BundleClient client(int port, SslContext ssl) {
        var client = BundleClient.builder("tls-bundle", "tls-bundle-1")
                .host("localhost").port(port).bundleVersion("1")
                .authToken(TOKEN)
                .handlerPayloadTypes(List.of("com.tls.echo"))
                .transportConfig(NettyTransportConfig.defaults().withSslContext(ssl))
                .build();
        client.registerRequestHandler("com.tls.echo", (payload, ctx) -> ("echo:" + new String(payload)).getBytes());
        return client;
    }
}
