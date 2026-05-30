package com.evento.server.web.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * The AuthService class is responsible for generating and decoding JWT tokens.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final static String ISSUER = "evento-server";
    private final static String KEY_ID = "evento-server-auth-key";
    /** The weak placeholder that used to ship in application.properties — rejected if presented. */
    private static final String LEGACY_WEAK_KEY = "evento-server-security-key";
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    /**
     * The AuthService is responsible for generating and decoding JWT tokens.
     *
     * <p>The HMAC signing key comes from {@code evento.security.signing.key} (typically the
     * {@code EVENTO_SECURITY_SIGNING_KEY} environment variable). To keep the published artifact
     * free of a shared known secret, no key ships by default: if none is configured — or the old
     * weak placeholder is supplied — a random per-process key is generated and a warning is logged.
     * That keeps local/dev runs working while ensuring production must supply a stable secret
     * (otherwise tokens are invalidated on every restart and across replicas).
     */
    public AuthService(@Value("${evento.security.signing.key:}") String signingKey) {
        if (signingKey == null || signingKey.isBlank() || LEGACY_WEAK_KEY.equals(signingKey)) {
            signingKey = randomKey();
            log.warn("event=jwt_signing_key_ephemeral detail=\"No (or weak default) "
                    + "evento.security.signing.key configured; generated a random per-process key. "
                    + "Issued tokens will not survive a restart or work across replicas. "
                    + "Set EVENTO_SECURITY_SIGNING_KEY in any real deployment.\"");
        }
        algorithm = Algorithm.HMAC256(signingKey);
        verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generates an admin token and a web token using the {@link #generateJWT(String, TokenRole[], long)} method.
     * The generated tokens are printed to the console.
     */
    @PostConstruct
    public void genAdminToken(){
        System.out.println();
        System.out.println("Admin token: " + generateJWT("evento-server",
                new TokenRole[]{TokenRole.ROLE_WEB, TokenRole.ROLE_ADMIN},
                1000 * 60 * 60 * 365));
        System.out.println("Web token: " + generateJWT("evento-web",
                new TokenRole[]{TokenRole.ROLE_WEB},
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
