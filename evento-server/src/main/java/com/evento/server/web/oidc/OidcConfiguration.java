package com.evento.server.web.oidc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code evento.server.web.auth=oidc}: the console signs in through the identity provider of the
 * client registration {@code evento.server.web.oidc.registration-id} (default {@code evento}), and
 * the provider's groups grant the console roles.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "evento.server.web.auth", havingValue = "oidc")
public class OidcConfiguration {

    @Bean
    OidcSignIn oidcSignIn(@Value("${evento.server.web.oidc.registration-id:evento}") String registrationId,
                          @Value("${evento.server.web.oidc.roles-claim:groups}") String rolesClaim,
                          @Value("${evento.server.web.oidc.web-role:evento-web}") String webRole,
                          @Value("${evento.server.web.oidc.admin-role:evento-admin}") String adminRole) {
        return new OidcSignIn(registrationId, new OidcRoles(rolesClaim, webRole, adminRole));
    }
}
