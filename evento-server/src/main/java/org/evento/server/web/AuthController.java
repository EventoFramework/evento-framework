package org.evento.server.web;

import org.evento.server.web.config.AuthService;
import org.evento.server.web.config.TokenRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping(value = "/token", produces = "text/plain")
    @Secured("ROLE_ADMIN")
    public ResponseEntity<String> generateToken(
            String clientId, TokenRole[] tokenRole, long duration
    ) {
        return ResponseEntity.ok(authService.generateJWT(
                clientId,
                tokenRole,
                duration
        ));
    }
}
