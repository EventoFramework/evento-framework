package org.evento.server.web.config;

import com.auth0.jwt.exceptions.TokenExpiredException;
import org.jetbrains.annotations.NotNull;
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

/**
 * This class represents an authentication filter that is used to authenticate incoming requests based on the provided token.
 * It extends the OncePerRequestFilter class from Spring Security, which ensures that the filter is only executed once per request.
 */
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    /**
     * This class represents an authentication filter that is used to authenticate incoming requests based on the provided token.
     * It extends the OncePerRequestFilter class from Spring Security, which ensures that the filter is only executed once per request.
     *
     * Example usage:
     *
     * AuthenticationService authService = new AuthenticationService();
     * AuthFilter authFilter = new AuthFilter(authService);
     * SecurityFilterChain filterChain = new SecurityFilterChain();
     * filterChain.addFilter(authFilter);
     * http.addFilterBefore(filterChain, UsernamePasswordAuthenticationFilter.class);
     *
     */
    public AuthFilter(AuthService authService) {
        this.authService = authService;
    }


    /**
     * Filters the incoming request based on the provided token for authentication.
     * If the token is valid and not expired, it sets the authentication context and proceeds to the next filter in the chain.
     * If the token is missing, blank, or invalid, it bypasses the authentication and continues to the next filter.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response
     * @param chain    the filter chain
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain) throws ServletException, IOException {

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
