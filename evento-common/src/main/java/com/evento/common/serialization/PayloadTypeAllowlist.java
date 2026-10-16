package com.evento.common.serialization;

import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * <p>So hardening is <b>opt-in</b> in 2.x:
 * <ul>
 *   <li><b>Open (default)</b> — accept any {@code Serializable} subtype (backwards compatible);
 *       a one-time warning is logged when the first such type is accepted.</li>
 *   <li><b>Restricted</b> — the blanket {@code Serializable} allowance is dropped in favour of an
 *       allowlist: {@code com.evento.} (framework) + a curated set of JDK value packages
 *       ({@code java.util.}, {@code java.time.}, {@code java.math.}) + application package prefixes.
 *       Anything else is refused. Two ways in, which add up:
 *       <ul>
 *         <li>{@code evento.serialization.allowed-packages} (system property) or
 *             {@code EVENTO_SERIALIZATION_ALLOWED_PACKAGES} (env), comma-separated;</li>
 *         <li>{@link #restrictTo(Collection)} from code: a bundle does it with its base package and
 *             {@code EventoBundle.Builder.setAllowedPayloadPackages}.</li>
 *       </ul></li>
 * </ul>
 *
 * <p>The validator is <b>live</b>: mappers built before {@link #restrictTo} see the restriction
 * too. Restrict before the first payload is read; a type already resolved by a mapper may stay
 * cached in it.
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

    /** Prefixes added from code by {@link #restrictTo}; non-empty means restricted. */
    private static final List<String> programmaticPackages = new CopyOnWriteArrayList<>();

    private PayloadTypeAllowlist() {}

    /**
     * Restrict polymorphic deserialization to the framework, the curated JDK value packages and
     * {@code packages} (plus any configured by property or env). Cumulative and irreversible for
     * the life of the JVM.
     *
     * @param packages package names or prefixes, e.g. {@code com.acme.orders}
     */
    public static void restrictTo(Collection<String> packages) {
        for (var p : packages) {
            if (p == null || p.isBlank()) continue;
            var prefix = p.trim().endsWith(".") ? p.trim() : p.trim() + ".";
            if (!programmaticPackages.contains(prefix)) programmaticPackages.add(prefix);
        }
        log.log(Level.INFO, "event=deserialization_allowlist application_packages={0}", applicationPackages());
    }

    /** True when only allowlisted packages are accepted. */
    public static boolean isRestricted() {
        return !applicationPackages().isEmpty();
    }

    /** Configured plus programmatic application prefixes. */
    static List<String> applicationPackages() {
        var all = new ArrayList<>(configuredPackages());
        all.addAll(programmaticPackages);
        return all;
    }

    /** Returns the configured application package prefixes (property takes precedence over env). */
    public static List<String> configuredPackages() {
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

    /** The validator for Evento's polymorphic mappers; see the class comment. */
    public static PolymorphicTypeValidator build() {
        return new LiveValidator();
    }

    /** For tests: back to the open default. */
    static void resetForTests() {
        programmaticPackages.clear();
        warnedOpen = false;
    }

    private static boolean allowed(String className, boolean serializable) {
        if (className.startsWith("com.evento.")) return true;
        var app = applicationPackages();
        if (app.isEmpty()) {
            if (serializable && !warnedOpen) {
                warnedOpen = true;
                log.log(Level.WARNING, "event=deserialization_open detail=\"polymorphic default typing accepts any "
                        + "Serializable subtype (gadget-chain risk); set {0} or {1} to a comma-separated "
                        + "application-package allowlist to harden\"", PROPERTY, ENV);
            }
            return serializable;
        }
        for (var p : JDK_VALUE_PACKAGES) if (className.startsWith(p)) return true;
        for (var p : app) if (className.startsWith(p)) return true;
        return false;
    }

    /** Reads the allowlist on every check, so a later {@link #restrictTo} applies to existing mappers. */
    private static final class LiveValidator extends PolymorphicTypeValidator.Base {
        @Override
        public Validity validateBaseType(MapperConfig<?> config, JavaType baseType) {
            return Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubClassName(MapperConfig<?> config,
                                             JavaType baseType, String subClassName) {
            // The class is not loaded yet: decide on the name when restricted, so a refused
            // gadget class is never even initialised.
            if (subClassName.startsWith("com.evento.")) return Validity.ALLOWED;
            if (isRestricted()) return allowed(subClassName, false) ? Validity.ALLOWED : Validity.DENIED;
            return Validity.INDETERMINATE;
        }

        @Override
        public Validity validateSubType(MapperConfig<?> config,
                                        JavaType baseType, JavaType subType) {
            var raw = subType.getRawClass();
            return allowed(raw.getName(), Serializable.class.isAssignableFrom(raw)) ? Validity.ALLOWED : Validity.DENIED;
        }
    }
}
