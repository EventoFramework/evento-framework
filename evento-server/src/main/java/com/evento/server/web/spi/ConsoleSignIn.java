package com.evento.server.web.spi;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * A sign-in for the web console other than HTTP Basic, such as the OpenID Connect sign-in
 * ({@link com.evento.server.web.oidc.OidcSignIn}, mode {@code oidc}). With {@code evento.server.web.auth} set to anything but {@code basic}, the server uses
 * the bean whose {@link #mode()} matches and refuses to start if there is none.
 *
 * <p>Before {@link #configure} runs, the server has set CORS, HTTP Basic (kept for machine clients
 * such as a Prometheus scrape) and the URL rules ({@code /api/**} and {@code /actuator/**}
 * authenticated, {@code /api/auth/mode} public). The sign-in adds its own flow, session and CSRF
 * protection, answers 401 on {@code /api/**} instead of redirecting, and grants {@code ROLE_WEB} /
 * {@code ROLE_ADMIN}: the controllers' {@code @Secured} rules stay the same.
 */
public interface ConsoleSignIn {

    /** The value of {@code evento.server.web.auth} this sign-in answers to; the GUI receives it too. */
    String mode();

    /** Where the GUI sends the user to sign in. */
    String loginUrl();

    /** Adds the sign-in to the console's security chain. */
    void configure(HttpSecurity http) throws Exception;

    /**
     * The sign-in for {@code mode}: empty for {@code basic}, the matching bean otherwise.
     *
     * @throws IllegalStateException if no bean provides the mode
     */
    static Optional<ConsoleSignIn> select(String mode, Stream<ConsoleSignIn> available) {
        var wanted = mode == null || mode.isBlank() ? "basic" : mode.trim();
        if (wanted.equalsIgnoreCase("basic")) return Optional.empty();
        return Optional.of(available.filter(s -> s.mode().equalsIgnoreCase(wanted)).findFirst()
                .orElseThrow(() -> new IllegalStateException("evento.server.web.auth=" + wanted
                        + ": no sign-in provides this mode. The server provides basic and oidc (single"
                        + " sign-on, with a client registration spring.security.oauth2.client.registration.*).")));
    }
}
