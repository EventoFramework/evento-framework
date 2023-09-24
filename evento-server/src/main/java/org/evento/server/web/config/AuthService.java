package org.evento.server.web.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final static String ISSUER = "evento-server";
    private final static String KEY_ID = "evento-server-auth-key";
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public AuthService(@Value("${evento.security.signing.key}") String signingKey) {
        algorithm = Algorithm.HMAC256(signingKey);
        verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
    }

    @PostConstruct
    public void genAdminToken(){
        System.out.println();
        System.out.println("Admin token: " + generateJWT("evento-server",
                new TokenRole[]{TokenRole.ROLE_ADMIN},
                1000 * 60 * 60 * 365));
        System.out.println();
    }

    public DecodedJWT decodeJWT(String token) {
        return verifier.verify(token);
    }

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
