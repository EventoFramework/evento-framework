package org.evento.server.web;

import org.evento.server.web.config.AuthService;
import org.evento.server.web.config.TokenRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AuthController class is responsible for handling authentication-related requests.
 */
@RestController
@RequestMapping("auth")
public class AuthController {

    private final AuthService authService;

    /**
     * The AuthController class is responsible for handling authentication-related requests.
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Generates a JWT token for the specified client ID, token roles, and duration.
     *
     * @param clientId   The ID of the client.
     * @param tokenRole  The array of token roles.
     * @param duration   The duration of the token in milliseconds.
     * @return The generated JWT token as a ResponseEntity.
     */
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
