package com.evento.server.web.oidc;

import com.evento.server.web.AuthController;
import com.evento.server.web.audit.AuditConfiguration;
import com.evento.server.web.config.WebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The server's security chain ({@link WebConfig}) with the OIDC sign-in plugged in. */
@SpringJUnitWebConfig(OidcSignInTest.Context.class)
@TestPropertySource(properties = {"evento.server.web.auth=oidc", "management.auditevents.enabled=false"})
class OidcSignInTest {

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
    @Import({WebConfig.class, AuthController.class, ProbeController.class, OidcConfiguration.class, AuditConfiguration.class})
    static class Context {
        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("evento").password("{noop}pw").roles("WEB", "ADMIN").build());
        }

        @Bean
        ClientRegistrationRepository clientRegistrations() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("evento")
                    .clientId("console")
                    .clientSecret("s")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("https://idp.example.com/authorize")
                    .tokenUri("https://idp.example.com/token")
                    .jwkSetUri("https://idp.example.com/jwks")
                    .userNameAttributeName("sub")
                    .build());
        }
    }

    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void mvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void theModeTellsTheGuiWhereToSignIn() throws Exception {
        mvc.perform(get("/api/auth/mode")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("oidc"))
                .andExpect(jsonPath("$.loginUrl").value("/oauth2/authorization/evento"));
    }

    @Test
    void signingInRedirectsToTheProvider() throws Exception {
        mvc.perform(get("/oauth2/authorization/evento")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://idp.example.com/authorize")))
                .andExpect(header().string("Location", containsString("code_challenge")));
    }

    @Test
    void theApiAnswers401InsteadOfRedirecting() throws Exception {
        mvc.perform(get("/api/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInUserGetsTheRolesItsGroupsGrant() throws Exception {
        var viewer = oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_WEB"));
        mvc.perform(get("/api/probe").with(viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/probe").with(viewer).with(csrf())).andExpect(status().isForbidden());
        var admin = oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_WEB"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post("/api/probe").with(admin).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void writesNeedTheCsrfToken() throws Exception {
        var admin = oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_WEB"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post("/api/probe").with(admin)).andExpect(status().isForbidden());
    }

    @Test
    void theCsrfCookieIsReadableByTheGui() throws Exception {
        mvc.perform(get("/api/auth/mode")).andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void logoutAnswers204() throws Exception {
        mvc.perform(post("/api/auth/logout").with(oidcLogin()).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void basicStaysAvailableForMachineClients() throws Exception {
        mvc.perform(get("/api/probe").with(httpBasic("evento", "pw"))).andExpect(status().isOk());
    }
}
