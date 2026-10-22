package com.evento.server.web.oidc;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the identity provider's groups or roles to the console's two roles.
 *
 * <ul>
 *   <li>{@code roles-claim}: where the user's groups are, a claim name or a dotted path into
 *       nested claims ({@code groups} for most providers, {@code roles} for Entra ID app roles,
 *       {@code realm_access.roles} for Keycloak realm roles); a list, or a string separated by
 *       commas or spaces;</li>
 *   <li>{@code web-role}: the value that grants {@code WEB} (read the console);</li>
 *   <li>{@code admin-role}: the value that grants {@code ADMIN} (consumer operations), and {@code WEB}.</li>
 * </ul>
 *
 * <p>A user with neither gets no role: the login succeeds, every API call answers 403.
 */
public final class OidcRoles implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final String rolesClaim;
    private final String webRole;
    private final String adminRole;

    public OidcRoles(String rolesClaim, String webRole, String adminRole) {
        this(new OidcUserService(), rolesClaim, webRole, adminRole);
    }

    OidcRoles(OAuth2UserService<OidcUserRequest, OidcUser> delegate, String rolesClaim, String webRole, String adminRole) {
        this.delegate = delegate;
        this.rolesClaim = rolesClaim;
        this.webRole = webRole;
        this.adminRole = adminRole;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        var user = delegate.loadUser(request);
        var authorities = new LinkedHashSet<GrantedAuthority>(user.getAuthorities());
        authorities.addAll(authorities(user.getClaims()));
        var nameKey = user.getClaims().containsKey("preferred_username") ? "preferred_username" : "sub";
        return user.getUserInfo() == null
                ? new DefaultOidcUser(authorities, user.getIdToken(), nameKey)
                : new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), nameKey);
    }

    /** The console roles the claims grant. */
    Set<GrantedAuthority> authorities(Map<String, Object> claims) {
        var groups = groups(claims);
        var out = new LinkedHashSet<GrantedAuthority>();
        boolean admin = groups.contains(adminRole);
        if (admin || groups.contains(webRole)) out.add(new SimpleGrantedAuthority("ROLE_WEB"));
        if (admin) out.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return out;
    }

    private List<String> groups(Map<String, Object> claims) {
        Object value = claims;
        for (var part : rolesClaim.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) return List.of();
            value = map.get(part);
        }
        if (value instanceof Collection<?> c) {
            var out = new ArrayList<String>();
            c.forEach(v -> out.add(String.valueOf(v)));
            return out;
        }
        if (value instanceof String s) {
            return Arrays.stream(s.split("[,\\s]+")).filter(x -> !x.isBlank()).toList();
        }
        return List.of();
    }
}
