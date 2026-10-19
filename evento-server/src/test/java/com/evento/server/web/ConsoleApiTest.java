package com.evento.server.web;

import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerResponseMessage;
import com.evento.server.bus.BusFacade;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.repository.core.BundleRepository;
import com.evento.server.domain.repository.core.ComponentRepository;
import com.evento.server.domain.repository.core.HandlerRepository;
import com.evento.server.domain.repository.core.PayloadRepository;
import com.evento.server.es.EventStore;
import com.evento.server.service.BundleService;
import com.evento.server.service.HandlerService;
import com.evento.server.service.discovery.ConsumerService;
import com.evento.server.service.performance.PerformanceStoreService;
import com.evento.server.web.config.WebConfig;
import com.evento.server.web.spi.AdminActionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The console's REST API with the real security chain ({@link WebConfig}), the services mocked: who
 * may call what, what the endpoints answer, and what the admin action listeners hear.
 */
@SpringJUnitWebConfig(ConsoleApiTest.Context.class)
@TestPropertySource(properties = {"evento.cluster.name=test-cluster", "application.version=9.9.9",
        "evento.telemetry.ttl=1"})
class ConsoleApiTest {

    @Configuration
    @EnableWebSecurity
    @Import({WebConfig.class, AdminActions.class,
            BundleController.class, CatalogController.class, ClusterStatusController.class,
            ConsumerController.class, DashboardController.class, HandlerController.class,
            PerformanceController.class, SystemStateStoreController.class})
    static class Context {
        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("viewer").password("{noop}pw").roles("WEB").build(),
                    User.withUsername("admin").password("{noop}pw").roles("WEB", "ADMIN").build());
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }

        @Bean BundleService bundleService() { return mock(BundleService.class); }
        @Bean HandlerService handlerService() { return mock(HandlerService.class); }
        @Bean ConsumerService consumerService() { return mock(ConsumerService.class); }
        @Bean BusFacade busFacade() { return mock(BusFacade.class); }
        @Bean PayloadRepository payloadRepository() { return mock(PayloadRepository.class); }
        @Bean ComponentRepository componentRepository() { return mock(ComponentRepository.class); }
        @Bean HandlerRepository handlerRepository() { return mock(HandlerRepository.class); }
        @Bean BundleRepository bundleRepository() { return mock(BundleRepository.class); }
        @Bean EventStore eventStore() { return mock(EventStore.class); }
        @Bean PerformanceStoreService performanceStoreService() { return mock(PerformanceStoreService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired RecordingListener heard;
    @Autowired BundleService bundleService;
    @Autowired ConsumerService consumerService;
    @Autowired ComponentRepository componentRepository;
    @Autowired EventStore eventStore;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reset(bundleService, consumerService, componentRepository, eventStore);
        when(eventStore.searchEvents(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 30), 0));
        when(eventStore.searchSnapshots(any(), anyInt(), anyInt(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 30), 0));
        heard.actions.clear();
    }

    // --- reads ---------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/bundle/", "/api/catalog/payload/", "/api/catalog/component/", "/api/handler/",
            "/api/dashboard", "/api/consumer/", "/api/cluster-status/attended-view",
            "/api/cluster-status/zones", "/api/performance/component", "/api/performance/aggregate",
            "/api/system-state-store/event", "/api/system-state-store/snapshot"})
    void readsNeedTheWebRole(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(httpBasic("viewer", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(httpBasic("viewer", "pw"))).andExpect(status().isOk());
    }

    @Test
    void theDashboardNamesTheClusterAndVersion() throws Exception {
        mvc.perform(get("/api/dashboard").with(httpBasic("viewer", "pw"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.serverName").value("test-cluster"))
                .andExpect(jsonPath("$.serverVersion").value("9.9.9"));
    }

    @Test
    void pagesOfMoreThanAThousandEventsAreRefused() throws Exception {
        mvc.perform(get("/api/system-state-store/event").param("size", "1001").with(httpBasic("viewer", "pw")))
                .andExpect(status().isBadRequest());
        verify(eventStore, never()).searchEvents(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void aComponentNameInSeveralBundlesAsksForTheId() throws Exception {
        var a = new Component();
        a.setId("orders-eu:OrderProjector");
        var b = new Component();
        b.setId("orders-us:OrderProjector");
        when(componentRepository.findById("OrderProjector")).thenReturn(Optional.empty());
        when(componentRepository.findAllByComponentNameOrderByBundle_Id("OrderProjector")).thenReturn(List.of(a, b));
        mvc.perform(get("/api/catalog/component/OrderProjector").with(httpBasic("viewer", "pw")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.candidates[0]").value("orders-eu:OrderProjector"))
                .andExpect(jsonPath("$.candidates[1]").value("orders-us:OrderProjector"));
    }

    // --- writes --------------------------------------------------------------------------------

    @Test
    void consumingTheDeadQueueNeedsTheAdminRole() throws Exception {
        var ok = new ConsumerResponseMessage();
        ok.setSuccess(true);
        when(consumerService.consumeDeadQueue(eq("orders-consumer"), any())).thenReturn(CompletableFuture.completedFuture(ok));

        mvc.perform(post("/api/consumer/orders-consumer/consume-dead-queue").with(httpBasic("viewer", "pw")))
                .andExpect(status().isForbidden());
        verify(consumerService, never()).consumeDeadQueue(anyString(), any());
        assertThat(heard.actions).isEmpty();

        var result = mvc.perform(post("/api/consumer/orders-consumer/consume-dead-queue").with(httpBasic("admin", "pw")))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        assertThat(heard.actions).containsExactly("consumer.dead_queue.consume {consumerId=orders-consumer} ok");
    }

    @Test
    void retryingAndDeletingADeadEventAreAdminActions() throws Exception {
        var ok = new ConsumerResponseMessage();
        ok.setSuccess(true);
        when(consumerService.setRetryForConsumerEvent(eq("c1"), eq(42L), eq(true), any()))
                .thenReturn(CompletableFuture.completedFuture(ok));
        when(consumerService.deleteDeadEventFromEventConsumer(eq("c1"), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(ok));

        perform(put("/api/consumer/c1/event/42").param("retry", "true").with(httpBasic("admin", "pw")));
        perform(delete("/api/consumer/c1/event/42").with(httpBasic("admin", "pw")));

        assertThat(heard.actions).containsExactly(
                "consumer.dead_event.retry {consumerId=c1, eventSequenceNumber=42, retry=true} ok",
                "consumer.dead_event.delete {consumerId=c1, eventSequenceNumber=42} ok");
    }

    @Test
    void aFailedActionEndsAsFailed() throws Exception {
        when(consumerService.consumeDeadQueue(eq("c1"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no instance of c1 is up")));

        var result = mvc.perform(post("/api/consumer/c1/consume-dead-queue").with(httpBasic("admin", "pw")))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            mvc.perform(asyncDispatch(result));
        } catch (Exception expected) {
            // No exception handler in this context: the failure surfaces to the caller.
        }

        assertThat(heard.actions).containsExactly("consumer.dead_queue.consume {consumerId=c1} no instance of c1 is up");
    }

    @Test
    void removingABundleNeedsTheAdminRole() throws Exception {
        when(bundleService.findById("orders")).thenReturn(new Bundle());

        mvc.perform(delete("/api/bundle/orders").with(httpBasic("viewer", "pw"))).andExpect(status().isForbidden());
        verify(bundleService, never()).unregister(anyString());

        mvc.perform(delete("/api/bundle/orders").with(httpBasic("admin", "pw"))).andExpect(status().isOk());
        verify(bundleService).unregister("orders");
        assertThat(heard.actions).containsExactly("bundle.unregister {bundleId=orders} ok");
    }

    @Test
    void theAuditTrailIsForAdminsOnly() throws Exception {
        mvc.perform(get("/actuator/auditevents").with(httpBasic("viewer", "pw"))).andExpect(status().isForbidden());
    }

    private void perform(RequestBuilder builder) throws Exception {
        var result = mvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    /** Hears the admin actions: name, target (sorted by key) and outcome. */
    static class RecordingListener implements AdminActionListener {
        final List<String> actions = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Completion started(String action, Map<String, Object> target) {
            return error -> actions.add(action + " " + new TreeMap<>(target) + " " + (error == null ? "ok" : error.getMessage()));
        }
    }
}
