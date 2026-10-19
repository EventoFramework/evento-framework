package com.evento.server.web.config;

import com.evento.server.web.AuthController;
import com.evento.server.web.spi.ConsoleSignIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The console's security filter chain ({@link WebConfig}) in a real Spring context: HTTP Basic
 * (default) and a {@link ConsoleSignIn} plugged in by mode.
 */
class WebSecurityTest {

    /** Stands for the console's controllers: one WEB endpoint, one ADMIN endpoint. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/probe")
        @Secured("ROLE_WEB")
        String read() {
            return "read";
        }

        @PostMapping("/api/probe")
        @Secured("ROLE_ADMIN")
        String write() {
            return "written";
        }
    }

    @Configuration
    @EnableWebSecurity
    @Import({WebConfig.class, AuthController.class, ProbeController.class})
    static class Context {
        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("evento").password("{noop}pw").roles("WEB", "ADMIN").build());
        }
    }

    /** A sign-in of mode {@code sso} that keeps a session and leaves CSRF on, as a real one would. */
    static class SsoSignIn implements ConsoleSignIn {
        volatile boolean configured;

        @Override
        public String mode() {
            return "sso";
        }

        @Override
        public String loginUrl() {
            return "/sso/start";
        }

        @Override
        public void configure(HttpSecurity http) {
            configured = true;
        }
    }

    @Configuration
    @Import(Context.class)
    static class SsoContext {
        @Bean
        SsoSignIn ssoSignIn() {
            return new SsoSignIn();
        }
    }

    abstract static class Base {
        @Autowired
        WebApplicationContext context;
        MockMvc mvc;

        @BeforeEach
        void mvc() {
            mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        }
    }

    @Nested
    @SpringJUnitWebConfig(SsoContext.class)
    class BasicMode extends Base {

        @Test
        void theModeIsPublic() throws Exception {
            mvc.perform(get("/api/auth/mode")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.mode").value("basic"))
                    .andExpect(jsonPath("$.loginUrl").doesNotExist());
        }

        @Test
        void theApiNeedsCredentialsAndAnswersAPlain401() throws Exception {
            mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
            mvc.perform(get("/api/auth/me").with(httpBasic("evento", "pw"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("evento"))
                    .andExpect(jsonPath("$.roles", containsInAnyOrder("WEB", "ADMIN")));
        }

        @Test
        void writesNeedNoCsrfTokenWithoutASession() throws Exception {
            mvc.perform(post("/api/probe").with(httpBasic("evento", "pw"))).andExpect(status().isOk());
        }

        @Test
        void aSignInOnTheClasspathStaysOffUntilItsModeIsChosen(@Autowired SsoSignIn sso) {
            assertThat(sso.configured).isFalse();
        }
    }

    @Nested
    @SpringJUnitWebConfig(SsoContext.class)
    @TestPropertySource(properties = "evento.server.web.auth=sso")
    class PluggedSignIn extends Base {

        @Test
        void theSignInOfTheModeConfiguresTheChain(@Autowired SsoSignIn sso) {
            assertThat(sso.configured).isTrue();
        }

        @Test
        void theModeTellsTheGuiWhereToSignIn() throws Exception {
            mvc.perform(get("/api/auth/mode")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.mode").value("sso"))
                    .andExpect(jsonPath("$.loginUrl").value("/sso/start"));
        }

        @Test
        void basicStaysAvailableForMachineClients() throws Exception {
            mvc.perform(get("/api/probe").with(httpBasic("evento", "pw"))).andExpect(status().isOk());
        }

        @Test
        void csrfIsLeftToTheSignIn() throws Exception {
            mvc.perform(post("/api/probe").with(httpBasic("evento", "pw"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void aModeNoSignInProvidesStopsTheServer() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Context.class);
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("test", Map.of("evento.server.web.auth", "oidc")));
            assertThatThrownBy(context::refresh).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("evento.server.web.auth=oidc")
                    .hasMessageContaining("Evento Enterprise");
        }
    }

    @Test
    void basicNeedsNoSignIn() {
        assertThat(ConsoleSignIn.select("basic", Stream.empty())).isEmpty();
        assertThat(ConsoleSignIn.select(" ", Stream.empty())).isEmpty();
    }
}
