package com.evento.server.web.config;

import com.evento.server.web.spi.ConsoleSignIn;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * Web layer configuration for the explorative (read-only) server.
 *
 * <p>The REST API is protected by <b>HTTP Basic auth</b> against the static in-memory user
 * configured via the standard Spring Boot properties {@code spring.security.user.name} /
 * {@code spring.security.user.password} (see {@code application.properties}; the user carries the
 * {@code WEB} and {@code ADMIN} roles the controllers' {@code @Secured} annotations require).
 * This replaces the previous JWT/Bearer scheme entirely: no token minting, no {@code /auth}
 * endpoint — credentials are validated on every request.
 *
 * <p>With {@code evento.server.web.auth} set to another mode, the {@link ConsoleSignIn} bean of
 * that mode adds its sign-in to the chain (single sign-on with OpenID Connect: mode {@code oidc},
 * {@link com.evento.server.web.oidc.OidcSignIn}); HTTP Basic stays available for machine clients such as a Prometheus scrape.
 */
@Configuration
@EnableWebMvc
@EnableMethodSecurity(
		securedEnabled = true)
public class WebConfig implements WebMvcConfigurer {

	/**
	 * Allowed CORS origins, from {@code evento.server.web.cors.allowed-origins} (comma-separated).
	 * Defaults to {@code *} to preserve existing behaviour; set explicit origins to lock the API down.
	 */
	@Value("${evento.server.web.cors.allowed-origins:*}")
	private List<String> allowedOrigins;

	/** {@code basic} (default), or the mode of a {@link ConsoleSignIn} bean. */
	@Value("${evento.server.web.auth:basic}")
	private String authMode;

	/**
	 * Plain 401 without a WWW-Authenticate header: the GUI has its own login page, and the header
	 * would trigger the browser's native credentials popup on top of it.
	 */
	private static final AuthenticationEntryPoint UNAUTHORIZED =
			(request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<ConsoleSignIn> signIns) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.httpBasic(b -> b.authenticationEntryPoint(UNAUTHORIZED))
				.authorizeHttpRequests(r -> r
						.requestMatchers("/actuator/health/**", "/actuator/info", "/api/auth/mode").permitAll()
						// Who did what in the console: for admins only (the audit trail, com.evento.server.web.audit).
						.requestMatchers("/actuator/auditevents/**").hasRole("ADMIN")
						.requestMatchers("/api/**", "/actuator/**").authenticated()
						.anyRequest().permitAll()); // static GUI assets + SPA fallback
		var signIn = ConsoleSignIn.select(authMode, signIns.orderedStream());
		if (signIn.isPresent()) {
			signIn.get().configure(http);
		} else {
			// Basic auth carries the credential on every request: no server session (no JSESSIONID
			// cookie to leak) and no ambient credential to forge requests with.
			http.csrf(AbstractHttpConfigurer::disable)
					.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		}
		return http.build();
	}


	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {

		registry.addResourceHandler("/**")
				.addResourceLocations("classpath:/public/")
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(@NotNull String resourcePath, @NotNull Resource location) throws IOException {
						Resource requestedResource = location.createRelative(resourcePath);
						return requestedResource.exists() && requestedResource.isReadable() ? requestedResource : new ClassPathResource("/public/index.html");
					}
				});
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("*"));
		configuration.setAllowedHeaders(List.of("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
