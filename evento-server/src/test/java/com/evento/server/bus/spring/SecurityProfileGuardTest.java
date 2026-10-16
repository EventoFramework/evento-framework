package com.evento.server.bus.spring;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityProfileGuardTest {

    private static final BusTlsProperties TLS_ON =
            new BusTlsProperties(true, "chain.pem", "key.pem", null, null, null, null);
    private static final BusTlsProperties TLS_OFF =
            new BusTlsProperties(false, null, null, null, null, null, null);

    /** What the shipped application.properties resolves to with no environment variable set. */
    private static MockEnvironment shipped() {
        return new MockEnvironment()
                .withProperty("evento.server.bus.auth-token", "")
                .withProperty("spring.security.user.password", "secret")
                .withProperty("spring.datasource.password", "secret")
                .withProperty("evento.server.web.cors.allowed-origins", "*");
    }

    private static MockEnvironment hardened() {
        return new MockEnvironment()
                .withProperty(SecurityProfileGuard.PROPERTY, "prod")
                .withProperty("evento.server.bus.auth-token", "a-long-random-token")
                .withProperty("spring.security.user.password", "another-secret")
                .withProperty("spring.datasource.password", "db-secret")
                .withProperty("evento.server.web.cors.allowed-origins", "https://console.example.com");
    }

    @Test
    void theShippedConfigurationFailsEveryCheck() {
        var guard = SecurityProfileGuard.evaluate(shipped(), TLS_OFF, false);
        assertThat(guard.profile()).isEqualTo(SecurityProfileGuard.Profile.DEV);
        assertThat(guard.violations()).hasSize(6);
    }

    @Test
    void devStartsWithFailingChecks() {
        SecurityProfileGuard.evaluate(shipped(), TLS_OFF, false).apply();
    }

    @Test
    void prodRefusesToStartAndListsEveryFailingCheck() {
        var env = shipped().withProperty(SecurityProfileGuard.PROPERTY, "prod");
        var guard = SecurityProfileGuard.evaluate(env, TLS_OFF, false);
        assertThatThrownBy(guard::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth-token")
                .hasMessageContaining("tls.enabled")
                .hasMessageContaining("allowed-packages")
                .hasMessageContaining("spring.security.user.password")
                .hasMessageContaining("cors")
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void prodStartsWhenEveryCheckPasses() {
        var guard = SecurityProfileGuard.evaluate(hardened(), TLS_ON, true);
        assertThat(guard.violations()).isEmpty();
        guard.apply();
    }

    @Test
    void aWildcardAmongExplicitOriginsStillFails() {
        var env = hardened().withProperty("evento.server.web.cors.allowed-origins", "https://a.example.com, *");
        assertThat(SecurityProfileGuard.evaluate(env, TLS_ON, true).violations())
                .singleElement().asString().contains("cors");
    }

    @Test
    void anUnknownProfileIsRejected() {
        var env = hardened().withProperty(SecurityProfileGuard.PROPERTY, "staging");
        assertThatThrownBy(() -> SecurityProfileGuard.evaluate(env, TLS_ON, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev or prod");
    }
}
