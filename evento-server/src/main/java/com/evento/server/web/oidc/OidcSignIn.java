package com.evento.server.web.oidc;

import com.evento.server.web.spi.ConsoleSignIn;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Single sign-on for the web console with the organisation's identity provider
 * ({@code evento.server.web.auth=oidc}): the server runs the OIDC authorization-code flow with PKCE
 * ({@code spring.security.oauth2.client.registration.<id>.*}), keeps the session in an HttpOnly
 * cookie, protects writes with a CSRF token the GUI reads from the {@code XSRF-TOKEN} cookie, and
 * maps the provider's groups to {@code WEB} / {@code ADMIN} ({@link OidcRoles}). No token reaches
 * the browser.
 */
public class OidcSignIn implements ConsoleSignIn {

    private static final AuthenticationEntryPoint UNAUTHORIZED =
            (request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

    private final String registrationId;
    private final OidcRoles roles;

    public OidcSignIn(String registrationId, OidcRoles roles) {
        this.registrationId = registrationId;
        this.roles = roles;
    }

    @Override
    public String mode() {
        return "oidc";
    }

    @Override
    public String loginUrl() {
        return "/oauth2/authorization/" + registrationId;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
                .oauth2Login(o -> o
                        .userInfoEndpoint(u -> u.oidcUserService(roles))
                        .defaultSuccessUrl("/dashboard", true))
                // The session cookie is ambient: writes need the token of the XSRF-TOKEN cookie in an
                // X-XSRF-TOKEN header, which another site cannot read.
                .csrf(c -> c
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // The API answers 401 instead of redirecting to the provider: the GUI decides.
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(UNAUTHORIZED,
                        request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/")))
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, auth) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)));
    }

    /** Issues the CSRF token on every request, so the XSRF-TOKEN cookie exists before the first write. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
