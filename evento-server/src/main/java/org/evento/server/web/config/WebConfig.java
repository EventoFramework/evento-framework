package org.evento.server.web.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
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
@EnableGlobalMethodSecurity(
		securedEnabled = true)
public class WebConfig implements WebMvcConfigurer {

	private final AuthService authService;

	public WebConfig(AuthService authService) {
		this.authService = authService;
	}


	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(r -> r.anyRequest().permitAll())
				.addFilterBefore(new AuthFilter(authService), UsernamePasswordAuthenticationFilter.class)
				.build();
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
		configuration.setAllowedOrigins(List.of("*"));
		configuration.setAllowedMethods(List.of("*"));
		configuration.setAllowedHeaders(List.of("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}