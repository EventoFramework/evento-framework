package com.evento.server.web;

import com.evento.server.web.spi.ConsoleSignIn;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How the web console signs in ({@code mode}, public) and who is signed in ({@code me}).
 * {@code basic} shows the username/password form; with a {@link ConsoleSignIn} the GUI sends the
 * user to its {@code loginUrl}.
 */
@RestController
@RequestMapping("api/auth")
public class AuthController {

    private final Optional<ConsoleSignIn> signIn;

    public AuthController(@Value("${evento.server.web.auth:basic}") String mode,
                          ObjectProvider<ConsoleSignIn> signIns) {
        this.signIn = ConsoleSignIn.select(mode, signIns.orderedStream());
    }

    @GetMapping("mode")
    public Map<String, String> mode() {
        var out = new LinkedHashMap<String, String>();
        out.put("mode", signIn.map(ConsoleSignIn::mode).orElse("basic"));
        signIn.ifPresent(s -> out.put("loginUrl", s.loginUrl()));
        return out;
    }

    @GetMapping("me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        return Map.of("name", authentication.getName(), "roles", roles);
    }
}
