package com.evento.server.service.discovery;

import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.server.bus.BusFacade;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.repository.core.BundleRepository;
import com.evento.server.domain.repository.core.ComponentRepository;
import com.evento.server.domain.repository.core.PayloadRepository;
import com.evento.server.service.BundleService;
import com.evento.server.service.HandlerService;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for the rolling-restart data wipe: the bundle row records the
 * instance that auto-registered it, and {@code onNodeLeave} reclaims the whole
 * registration (handlers, components, consumers, bundle) when that instance
 * departs. During a rolling restart the replacement instance joins before the
 * old socket dies, so without ownership transfer on join — and a liveness check
 * on leave — the stale departure deleted the rows the live instance had just
 * registered, and its subsequent consumer registration failed on missing
 * component rows.
 */
class AutoDiscoveryServiceLifecycleTest {

    private static final String BUNDLE = "lab-bundle";

    private BusFacade busFacade;
    private BundleRepository bundleRepository;
    private HandlerService handlerService;
    private BundleService bundleService;
    private ConsumerService consumerService;
    private Consumer<BusEvent> subscriber;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        busFacade = mock(BusFacade.class);
        bundleRepository = mock(BundleRepository.class);
        handlerService = mock(HandlerService.class);
        bundleService = mock(BundleService.class);
        consumerService = mock(ConsumerService.class);
        // Null DataSource → PgDistributedLock stays JVM-local (embedded/test mode).
        new AutoDiscoveryService(busFacade, bundleRepository, handlerService,
                mock(PayloadRepository.class), bundleService,
                mock(ComponentRepository.class), consumerService, null);
        var captor = ArgumentCaptor.forClass(Consumer.class);
        verify(busFacade).subscribe(captor.capture());
        subscriber = captor.getValue();
    }

    private static Bundle ephemeralBundle(String ownerInstanceId) {
        return new Bundle(BUNDLE, 3, "", "", "", "", ownerInstanceId, true, Instant.now());
    }

    private static NodeAddress node(String instanceId) {
        return new NodeAddress(BUNDLE, 3, instanceId);
    }

    @Test
    void joinTransfersEphemeralBundleOwnershipToTheJoiningInstance() {
        var bundle = ephemeralBundle("old-instance");
        when(bundleRepository.findById(BUNDLE)).thenReturn(Optional.of(bundle));
        when(handlerService.exists(any(), any(), any(), any(), any())).thenReturn(true);
        when(handlerService.findAllByBundleId(BUNDLE)).thenReturn(new ArrayList<>());
        var handler = mock(RegisteredHandler.class);
        when(handler.getComponentName()).thenReturn("OrderProjector");
        var discovery = new BundleDiscoveryInfo(3, "", "", "", "", List.of(handler), null);

        subscriber.accept(new BusEvent.BundleDiscovered(node("new-instance"), discovery, Instant.now()));

        var saved = ArgumentCaptor.forClass(Bundle.class);
        verify(bundleRepository).save(saved.capture());
        assertThat(saved.getValue().getInstanceId()).isEqualTo("new-instance");
    }

    @Test
    void staleLeaveKeepsRegistrationWhileAnotherInstanceOfTheBundleIsLive() {
        when(bundleRepository.findById(BUNDLE)).thenReturn(Optional.of(ephemeralBundle("old-instance")));
        when(busFacade.currentView()).thenReturn(Set.of(node("new-instance")));

        subscriber.accept(new BusEvent.NodeLeft(node("old-instance"), "socket_closed", Instant.now()));

        verify(consumerService).clearInstance("old-instance");
        verify(bundleService, never()).unregister(any());
    }

    @Test
    void leaveOfTheLastInstanceReclaimsTheEphemeralBundle() {
        when(bundleRepository.findById(BUNDLE)).thenReturn(Optional.of(ephemeralBundle("old-instance")));
        when(busFacade.currentView()).thenReturn(Set.of());

        subscriber.accept(new BusEvent.NodeLeft(node("old-instance"), "socket_closed", Instant.now()));

        verify(consumerService).clearInstance("old-instance");
        verify(bundleService).unregister(BUNDLE);
    }

    @Test
    void leaveNeverReclaimsAManuallyPublishedBundle() {
        when(bundleRepository.findById(BUNDLE)).thenReturn(Optional.of(ephemeralBundle(null)));
        when(busFacade.currentView()).thenReturn(Set.of());

        subscriber.accept(new BusEvent.NodeLeft(node("old-instance"), "socket_closed", Instant.now()));

        verify(bundleService, never()).unregister(any());
    }
}
