package com.evento.server.web.audit;

import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerResponseMessage;
import com.evento.server.bus.BusFacade;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.service.BundleService;
import com.evento.server.service.HandlerService;
import com.evento.server.service.discovery.ConsumerService;
import com.evento.server.web.AdminActions;
import com.evento.server.web.BundleController;
import com.evento.server.web.ConsumerController;
import com.evento.server.web.HandlerController;
import com.evento.server.web.config.WebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.actuate.audit.listener.AuditListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit trail on the server's real console controllers and security chain, the services
 * mocked: what each action, sign-in and refusal records.
 */
@SpringJUnitWebConfig(AuditTrailTest.Context.class)
class AuditTrailTest {

    @Configuration
    @EnableWebSecurity
    @Import({WebConfig.class, AdminActions.class, AuditConfiguration.class,
            BundleController.class, ConsumerController.class, HandlerController.class})
    static class Context {
        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("viewer").password("{noop}pw").roles("WEB").build(),
                    User.withUsername("admin").password("{noop}pw").roles("WEB", "ADMIN").build());
        }

        /** What Spring Boot's audit and security auto-configuration add in the real server. */
        @Bean
        AuditListener auditListener(AuditEventRepository repository) {
            return new AuditListener(repository);
        }

        @Bean
        DefaultAuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
            return new DefaultAuthenticationEventPublisher(publisher);
        }

        @Bean BundleService bundleService() { return mock(BundleService.class); }
        @Bean HandlerService handlerService() { return mock(HandlerService.class); }
        @Bean ConsumerService consumerService() { return mock(ConsumerService.class); }
        @Bean BusFacade busFacade() { return mock(BusFacade.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired AuditEventRepository audit;
    @Autowired BundleService bundleService;
    @Autowired ConsumerService consumerService;

    MockMvc mvc;
    Instant testStart;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reset(bundleService, consumerService);
        testStart = Instant.now().minusNanos(1);
    }

    @Test
    void anAdminActionRecordsWhoWhatFromWhereAndTheOutcome() throws Exception {
        var ok = new ConsumerResponseMessage();
        ok.setSuccess(true);
        when(consumerService.consumeDeadQueue(eq("orders-consumer"), any())).thenReturn(CompletableFuture.completedFuture(ok));

        perform(post("/api/consumer/orders-consumer/consume-dead-queue").with(httpBasic("admin", "pw")));

        var action = only("ADMIN_ACTION");
        assertThat(action.getPrincipal()).isEqualTo("admin");
        assertThat(action.getData())
                .containsEntry("action", "consumer.dead_queue.consume")
                .containsEntry("consumerId", "orders-consumer")
                .containsEntry("outcome", "ok")
                .containsKey("remoteAddress");
    }

    @Test
    void aRefusedActionRecordsTheSignedInUser() throws Exception {
        mvc.perform(post("/api/consumer/orders-consumer/consume-dead-queue").with(httpBasic("viewer", "pw")))
                .andExpect(status().isForbidden());
        assertThat(only("AUTHORIZATION_FAILURE").getPrincipal()).isEqualTo("viewer");
        assertThat(audit.find(null, testStart, "ADMIN_ACTION")).isEmpty();
    }

    @Test
    void retryingAndDeletingADeadEventAreRecorded() throws Exception {
        var ok = new ConsumerResponseMessage();
        ok.setSuccess(true);
        when(consumerService.setRetryForConsumerEvent(eq("c1"), eq(42L), eq(true), any()))
                .thenReturn(CompletableFuture.completedFuture(ok));
        when(consumerService.deleteDeadEventFromEventConsumer(eq("c1"), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(ok));

        perform(put("/api/consumer/c1/event/42").param("retry", "true").with(httpBasic("admin", "pw")));
        perform(delete("/api/consumer/c1/event/42").with(httpBasic("admin", "pw")));

        var actions = audit.find("admin", testStart, "ADMIN_ACTION");
        assertThat(actions).extracting(e -> e.getData().get("action"))
                .containsExactly("consumer.dead_event.retry", "consumer.dead_event.delete");
        assertThat(actions.getFirst().getData()).containsEntry("eventSequenceNumber", 42L).containsEntry("retry", true);
    }

    @Test
    void aFailedActionIsRecordedAsFailed() throws Exception {
        when(consumerService.consumeDeadQueue(eq("c1"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no instance of c1 is up")));

        var result = mvc.perform(post("/api/consumer/c1/consume-dead-queue").with(httpBasic("admin", "pw")))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            mvc.perform(asyncDispatch(result));
        } catch (Exception expected) {
            // No exception handler in this context: the failure surfaces to the caller.
        }

        assertThat(only("ADMIN_ACTION").getData())
                .containsEntry("outcome", "failed")
                .containsEntry("error", "IllegalStateException: no instance of c1 is up");
    }

    @Test
    void removingABundleIsRecorded() throws Exception {
        when(bundleService.findById("orders")).thenReturn(new Bundle());
        mvc.perform(delete("/api/bundle/orders").with(httpBasic("admin", "pw"))).andExpect(status().isOk());
        assertThat(only("ADMIN_ACTION").getData())
                .containsEntry("action", "bundle.unregister")
                .containsEntry("bundleId", "orders")
                .containsEntry("outcome", "ok");
    }

    @Test
    void aWrongPasswordIsRecordedButBasicSuccessesAreNot() throws Exception {
        mvc.perform(get("/api/handler/").with(httpBasic("admin", "nope"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/handler/").with(httpBasic("admin", "pw"))).andExpect(status().isOk());

        assertThat(only("AUTHENTICATION_FAILURE").getPrincipal()).isEqualTo("admin");
        assertThat(audit.find(null, testStart, "AUTHENTICATION_SUCCESS")).isEmpty();
    }

    @Test
    void anonymousRefusalsAreNotRecorded() throws Exception {
        mvc.perform(get("/api/handler/")).andExpect(status().isUnauthorized());
        assertThat(audit.find(null, testStart, null)).isEmpty();
    }

    @Test
    void theAuditTrailIsForAdminsOnly() throws Exception {
        mvc.perform(get("/actuator/auditevents").with(httpBasic("viewer", "pw"))).andExpect(status().isForbidden());
    }

    private void perform(RequestBuilder builder) throws Exception {
        var result = mvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    private AuditEvent only(String type) {
        var events = audit.find(null, testStart, type);
        assertThat(events).as("%s events", type).hasSize(1);
        return events.getFirst();
    }
}
