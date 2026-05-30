package com.evento.common.serialization;

import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the {@link PolymorphicTypeValidator} used by Evento's polymorphic CBOR/JSON mappers
 * (payload mapper + admin codec), which run with {@code activateDefaultTyping(NON_FINAL)} so a
 * type discriminator on the wire/in the DB drives instantiation.
 *
 * <p>The historical default allows <em>any</em> {@link Serializable} subtype — the canonical Jackson
 * default-typing RCE surface (commons-collections / spring-beans / c3p0 … gadgets, when present). It
 * cannot simply be removed: user domain payloads (events, commands, queries, aggregate state) are
 * {@code Serializable} and live in arbitrary application packages, and they legitimately traverse
 * these mappers (event-store persistence, gateways, query responses, command forwarding).
 *
 * <p>So hardening is <b>opt-in and operator-driven</b>:
 * <ul>
 *   <li><b>Unset (default)</b> — accept any {@code Serializable} subtype (backwards compatible);
 *       a one-time warning is logged.</li>
 *   <li><b>Set</b> {@code evento.serialization.allowed-packages} (system property) or
 *       {@code EVENTO_SERIALIZATION_ALLOWED_PACKAGES} (env), comma-separated — the blanket
 *       {@code Serializable} allowance is dropped in favour of an allowlist: {@code com.evento.}
 *       (framework) + a curated set of JDK value packages ({@code java.util.}, {@code java.time.},
 *       {@code java.math.}) + the configured application package prefixes. Anything else is refused.</li>
 * </ul>
 *
 * <p>Note the JDK allowlist deliberately omits {@code java.lang.} and {@code java.net.} (which contain
 * gadget-relevant types like {@code ProcessBuilder}/{@code Runtime}/{@code URL}); final value types
 * such as {@code String}/{@code Integer} do not carry a type discriminator under {@code NON_FINAL}
 * and so need no allowlisting.
 */
public final class PayloadTypeAllowlist {

    private static final Logger log = System.getLogger(PayloadTypeAllowlist.class.getName());

    public static final String PROPERTY = "evento.serialization.allowed-packages";
    public static final String ENV = "EVENTO_SERIALIZATION_ALLOWED_PACKAGES";

    /** Curated JDK value-type packages safe for polymorphic deserialization. */
    private static final List<String> JDK_VALUE_PACKAGES = List.of("java.util.", "java.time.", "java.math.");

    private static volatile boolean warnedOpen = false;

    private PayloadTypeAllowlist() {}

    /** Returns the configured application package prefixes (property takes precedence over env). */
    static List<String> configuredPackages() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(ENV);
        }
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Build the validator from current configuration. Read at mapper-build time, so set the
     * property/env before the first payload mapper is created.
     */
    public static PolymorphicTypeValidator build() {
        var appPackages = configuredPackages();
        var builder = BasicPolymorphicTypeValidator.builder();

        if (appPackages.isEmpty()) {
            if (!warnedOpen) {
                warnedOpen = true;
                log.log(Level.WARNING, "event=deserialization_open detail=\"polymorphic default typing accepts any "
                        + "Serializable subtype (gadget-chain risk); set {0} or {1} to a comma-separated "
                        + "application-package allowlist to harden\"", PROPERTY, ENV);
            }
            return builder
                    .allowIfSubType(Serializable.class)
                    .allowIfSubType("com.evento.")
                    .build();
        }

        builder.allowIfSubType("com.evento.");
        JDK_VALUE_PACKAGES.forEach(builder::allowIfSubType);
        appPackages.forEach(builder::allowIfSubType);
        log.log(Level.INFO, "event=deserialization_allowlist application_packages={0}", appPackages);
        return builder.build();
    }
}
