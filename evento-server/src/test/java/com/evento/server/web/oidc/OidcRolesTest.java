package com.evento.server.web.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OidcRolesTest {

    private static List<String> roles(OidcRoles mapper, Map<String, Object> claims) {
        return mapper.authorities(claims).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void theWebGroupGrantsWebOnly() {
        var mapper = new OidcRoles("groups", "evento-web", "evento-admin");
        assertThat(roles(mapper, Map.of("groups", List.of("staff", "evento-web")))).containsExactly("ROLE_WEB");
    }

    @Test
    void theAdminGroupGrantsBoth() {
        var mapper = new OidcRoles("groups", "evento-web", "evento-admin");
        assertThat(roles(mapper, Map.of("groups", List.of("evento-admin")))).containsExactly("ROLE_WEB", "ROLE_ADMIN");
    }

    @Test
    void aNestedClaimLikeKeycloakRealmRoles() {
        var mapper = new OidcRoles("realm_access.roles", "viewer", "operator");
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of("operator", "offline_access")));
        assertThat(roles(mapper, claims)).containsExactly("ROLE_WEB", "ROLE_ADMIN");
    }

    @Test
    void aClaimAsASeparatedString() {
        var mapper = new OidcRoles("roles", "evento-web", "evento-admin");
        assertThat(roles(mapper, Map.of("roles", "a, evento-web b"))).containsExactly("ROLE_WEB");
    }

    @Test
    void noMatchingGroupOrNoClaimGrantsNothing() {
        var mapper = new OidcRoles("groups", "evento-web", "evento-admin");
        assertThat(roles(mapper, Map.of("groups", List.of("staff")))).isEmpty();
        assertThat(roles(mapper, Map.of("email", "x@example.com"))).isEmpty();
        assertThat(roles(new OidcRoles("a.b", "w", "x"), Map.of("a", "not a map"))).isEmpty();
    }
}
