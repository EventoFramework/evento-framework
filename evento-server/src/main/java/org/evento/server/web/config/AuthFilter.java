package org.evento.server.web.config;

import com.auth0.jwt.exceptions.TokenExpiredException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class AuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public AuthFilter(AuthService authService) {
        this.authService = authService;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        // Get authorization header and validate
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank() || !header.startsWith("Bearer ")) {
            header = request.getParameter("token");
            if (header == null || header.isBlank()) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Get jwt token and validate
        final String token = header.replace("Bearer ", "");
        if (token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }


        try {
            var validToken = authService.decodeJWT(token);
            SecurityContextHolder.getContext().setAuthentication(new Authentication() {
                @Override
                public Collection<? extends GrantedAuthority> getAuthorities() {
                    return validToken.getClaim("role").asList(String.class)
                            .stream().map(s -> (GrantedAuthority) () -> s).toList();
                }

                @Override
                public Object getCredentials() {
                    return validToken;
                }

                @Override
                public Object getDetails() {
                    return validToken;
                }

                @Override
                public Object getPrincipal() {
                    return validToken.getClaim("clientId").asString();
                }

                @Override
                public boolean isAuthenticated() {
                    return true;
                }

                @Override
                public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {

                }

                @Override
                public String getName() {
                    return validToken.getClaim("clientId").asString();
                }
            });
            chain.doFilter(request, response);
        }catch (TokenExpiredException tokenExpiredException){
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
        }

    }
}
