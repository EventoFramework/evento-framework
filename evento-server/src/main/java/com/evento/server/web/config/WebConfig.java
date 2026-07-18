package com.evento.server.web.config;

import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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
	@org.springframework.beans.factory.annotation.Value("${evento.server.web.cors.allowed-origins:*}")
	private List<String> allowedOrigins;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				// Basic auth carries the credential on every request; no server session needed
				// (and no JSESSIONID cookie to leak).
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Plain 401 without a WWW-Authenticate header: the GUI has its own login page,
				// and the header would trigger the browser's native credentials popup on top of it.
				.httpBasic(b -> b.authenticationEntryPoint(
						(request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
				.authorizeHttpRequests(r -> r
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/api/**", "/actuator/**").authenticated()
						.anyRequest().permitAll()); // static GUI assets + SPA fallback
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
