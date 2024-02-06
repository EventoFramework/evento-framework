package com.evento.server.web.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * The AuthService class is responsible for generating and decoding JWT tokens.
 */
@Service
public class AuthService {

    private final static String ISSUER = "evento-server";
    private final static String KEY_ID = "evento-server-auth-key";
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    /**
     * The AuthService class is responsible for generating and decoding JWT tokens.
     */
    public AuthService(@Value("${evento.security.signing.key}") String signingKey) {
        algorithm = Algorithm.HMAC256(signingKey);
        verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
    }

    /**
     * Generates an admin token and a web token using the {@link #generateJWT(String, TokenRole[], long)} method.
     * The generated tokens are printed to the console.
     */
    @PostConstruct
    public void genAdminToken(){
        System.out.println();
        System.out.println("Admin token: " + generateJWT("evento-server",
                new TokenRole[]{TokenRole.ROLE_ADMIN},
                1000 * 60 * 60 * 365));
        System.out.println("Web token: " + generateJWT("evento-web",
                new TokenRole[]{TokenRole.ROLE_WEB, TokenRole.ROLE_DEPLOY, TokenRole.ROLE_ADMIN},
                1000 * 60 * 60 * 365));
        System.out.println();
    }

    /**
     * Decodes the given JWT token.
     *
     * @param token the JWT token to decode
     * @return the decoded JWT object
     */
    public DecodedJWT decodeJWT(String token) {
        return verifier.verify(token);
    }

    /**
     * Generates a JSON Web Token (JWT) with the specified client ID, token roles, and duration.
     *
     * @param clientId  The ID of the client.
     * @param roles     An array of token roles.
     * @param duration  The duration of the token in milliseconds.
     * @return The generated JWT token.
     */
    public String generateJWT(String clientId, TokenRole[] roles, long duration) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withClaim("clientId", clientId)
                .withClaim("role", Arrays.stream(roles).map(Enum::toString).toList())
                .withIssuedAt(new Date())
                .withKeyId(KEY_ID)
                .withExpiresAt(new Date(System.currentTimeMillis() + duration))
                .withJWTId(UUID.randomUUID()
                        .toString())
                .withNotBefore(new Date(System.currentTimeMillis() - 1000L))
                .sign(algorithm);
    }
}
