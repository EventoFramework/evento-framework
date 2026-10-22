package com.evento.server.bus.spring;

import com.evento.common.serialization.PayloadTypeAllowlist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Checks the server's hardening against {@code evento.server.security.profile}
 * ({@code EVENTO_SERVER_SECURITY_PROFILE}).
 *
 * <ul>
 *   <li>{@code prod} (the default since 3.0): the server refuses to start while any check fails,
 *       before the bus port opens, and the exception lists all of them.</li>
 *   <li>{@code dev} (set it for development, tests and demos): every check that fails is listed
 *       once at startup ({@code event=security_profile_dev}), and the server starts.</li>
 * </ul>
 *
 * <p>The checks: a bundle auth token; TLS on the bus; a deserialization allowlist; a web console
 * password other than the shipped {@code secret}; explicit CORS origins; a database password other
 * than the shipped {@code secret}.
 */
public final class SecurityProfileGuard {

    private static final Logger log = LoggerFactory.getLogger(SecurityProfileGuard.class);

    public static final String PROPERTY = "evento.server.security.profile";
    static final String SHIPPED_PASSWORD = "secret";

    public enum Profile { DEV, PROD }

    private final Profile profile;
    private final List<String> violations;

    SecurityProfileGuard(Profile profile, List<String> violations) {
        this.profile = profile;
        this.violations = List.copyOf(violations);
    }

    /** Evaluates the environment and fails in {@code prod} when a check does not pass. */
    public static SecurityProfileGuard enforce(Environment env, BusTlsProperties tls) {
        var guard = evaluate(env, tls, PayloadTypeAllowlist.isRestricted());
        guard.apply();
        return guard;
    }

    static SecurityProfileGuard evaluate(Environment env, BusTlsProperties tls, boolean allowlistConfigured) {
        var raw = env.getProperty(PROPERTY, "prod").trim().toUpperCase(Locale.ROOT);
        Profile profile;
        try {
            profile = Profile.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(PROPERTY + " must be dev or prod, got '" + raw.toLowerCase(Locale.ROOT) + "'");
        }
        var violations = new ArrayList<String>();
        if (env.getProperty("evento.server.bus.auth-token", "").isBlank()) {
            violations.add("evento.server.bus.auth-token is not set: any bundle can register");
        }
        if (tls == null || !tls.enabled()) {
            violations.add("evento.server.bus.tls.enabled is false: the bus carries payloads and the token in clear");
        }
        if (!allowlistConfigured) {
            violations.add(PayloadTypeAllowlist.PROPERTY + " (" + PayloadTypeAllowlist.ENV
                    + ") is not set: polymorphic deserialization accepts any Serializable type");
        }
        if (SHIPPED_PASSWORD.equals(env.getProperty("spring.security.user.password"))) {
            violations.add("spring.security.user.password (SPRING_SECURITY_USER_PASSWORD) is the shipped 'secret'");
        }
        var origins = env.getProperty("evento.server.web.cors.allowed-origins", "*");
        if (Arrays.stream(origins.split(",")).map(String::trim).anyMatch("*"::equals)) {
            violations.add("evento.server.web.cors.allowed-origins allows any origin (*)");
        }
        if (SHIPPED_PASSWORD.equals(env.getProperty("spring.datasource.password"))) {
            violations.add("spring.datasource.password (SPRING_DATASOURCE_PASSWORD) is the shipped 'secret'");
        }
        return new SecurityProfileGuard(profile, violations);
    }

    void apply() {
        if (violations.isEmpty()) {
            log.info("event=security_profile profile={} checks=passed", profile.name().toLowerCase(Locale.ROOT));
            return;
        }
        if (profile == Profile.PROD) {
            throw new IllegalStateException(PROPERTY + "=prod, the server will not start until these are fixed:\n  - "
                    + String.join("\n  - ", violations));
        }
        log.warn("event=security_profile_dev failing_checks={} detail=\"not for production; with {}=prod the "
                        + "server would refuse to start:\n  - {}\"",
                violations.size(), PROPERTY, String.join("\n  - ", violations));
    }

    public Profile profile() {
        return profile;
    }

    public List<String> violations() {
        return violations;
    }
}
