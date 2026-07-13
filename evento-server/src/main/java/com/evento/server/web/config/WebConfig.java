package com.evento.server.web.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebMvc
@EnableMethodSecurity(
		securedEnabled = true)
public class WebConfig implements WebMvcConfigurer {

	private final AuthService authService;

	/**
	 * Allowed CORS origins, from {@code evento.server.web.cors.allowed-origins} (comma-separated).
	 * Defaults to {@code *} to preserve existing behaviour; set explicit origins to lock the API down.
	 */
	@org.springframework.beans.factory.annotation.Value("${evento.server.web.cors.allowed-origins:*}")
	private List<String> allowedOrigins;

	public WebConfig(AuthService authService) {
		this.authService = authService;
	}


	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebConfig.class);

	/**
	 * Web security posture, from {@code evento.server.web.security.mode}:
	 * <ul>
	 *   <li>{@code open} (default) — every request is permitted at the HTTP layer; controller
	 *       {@code @Secured} method annotations still apply where present. Preserves prior behaviour.</li>
	 *   <li>{@code token} — {@code /api/**} and sensitive actuator endpoints require a valid JWT
	 *       (via {@link AuthFilter}); static GUI assets and {@code /actuator/health|info} stay public
	 *       so the dashboard loads and orchestrator probes work. (The GUI still needs a token-injection
	 *       flow before it can call the API in this mode.)</li>
	 * </ul>
	 */
	@org.springframework.beans.factory.annotation.Value("${evento.server.web.security.mode:open}")
	private String securityMode;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(new AuthFilter(authService), UsernamePasswordAuthenticationFilter.class);

		if ("token".equalsIgnoreCase(securityMode)) {
			http.authorizeHttpRequests(r -> r
					.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
					.requestMatchers("/api/**", "/actuator/**").authenticated()
					.anyRequest().permitAll()); // static GUI assets + SPA fallback
			log.info("event=web_security mode=token detail=\"/api/** and sensitive actuator endpoints require a valid token\"");
		} else {
			if (!"open".equalsIgnoreCase(securityMode)) {
				log.warn("event=web_security_unknown_mode mode={} detail=\"defaulting to open\"", securityMode);
			}
			http.authorizeHttpRequests(r -> r.anyRequest().permitAll());
			log.warn("event=web_security mode=open detail=\"REST API is unauthenticated at the HTTP layer; "
					+ "set evento.server.web.security.mode=token to require JWTs\"");
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