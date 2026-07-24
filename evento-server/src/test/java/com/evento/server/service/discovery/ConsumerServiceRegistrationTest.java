package com.evento.server.service.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Consumer;
import com.evento.server.domain.repository.core.BundleRepository;
import com.evento.server.domain.repository.core.ComponentRepository;
import com.evento.server.domain.repository.core.ConsumerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for the consumer-registration race: {@code registerConsumers} used
 * {@code getReferenceById} lazy proxies, so when a component row was missing —
 * discovery not yet committed, or a stale {@code NodeLeft} had just wiped the
 * bundle — the flush blew up with {@code ObjectNotFoundException} and the outer
 * catch silently dropped the whole registration (empty Consumers tab until the
 * next reconnect). The fix resolves each component up front and recreates an
 * ephemeral row (and bundle) when it is gone; discovery backfills the metadata.
 */
class ConsumerServiceRegistrationTest {

    private ComponentRepository componentRepository;
    private ConsumerRepository consumerRepository;
    private BundleRepository bundleRepository;
    private ConsumerService service;

    @BeforeEach
    void setUp() {
        componentRepository = mock(ComponentRepository.class);
        consumerRepository = mock(ConsumerRepository.class);
        bundleRepository = mock(BundleRepository.class);
        // Null DataSource → PgDistributedLock stays JVM-local (embedded/test mode).
        service = new ConsumerService(componentRepository, consumerRepository,
                bundleRepository, "test-server", null);
    }

    private static BundleConsumerRegistrationMessage registration(
            HashMap<String, HashSet<String>> projectors,
            HashMap<String, HashSet<String>> sagas,
            HashMap<String, HashSet<String>> observers) {
        var cr = new BundleConsumerRegistrationMessage();
        cr.setProjectorConsumers(projectors);
        cr.setSagaConsumers(sagas);
        cr.setObserverConsumers(observers);
        return cr;
    }

    private static HashMap<String, HashSet<String>> byComponent(String componentName, String... consumerIds) {
        var map = new HashMap<String, HashSet<String>>();
        map.put(componentName, new HashSet<>(Set.of(consumerIds)));
        return map;
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingComponentIsRecreatedInsteadOfDroppingTheRegistration() {
        when(componentRepository.findById("OrderObserver")).thenReturn(Optional.empty());
        when(bundleRepository.findById("lab-bundle")).thenReturn(Optional.empty());
        when(bundleRepository.save(any(Bundle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(componentRepository.save(any(Component.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registerConsumers("lab-bundle", "instance-1", 3,
                registration(new HashMap<>(), new HashMap<>(),
                        byComponent("OrderObserver", "OrderObserver_consumer")));

        var componentCaptor = ArgumentCaptor.forClass(Component.class);
        verify(componentRepository).save(componentCaptor.capture());
        var component = componentCaptor.getValue();
        assertThat(component.getComponentName()).isEqualTo("OrderObserver");
        assertThat(component.getComponentType()).isEqualTo(ComponentType.Observer);
        assertThat(component.getBundle().getId()).isEqualTo("lab-bundle");
        assertThat(component.getBundle().getInstanceId()).isEqualTo("instance-1");

        var consumersCaptor = ArgumentCaptor.forClass(List.class);
        verify(consumerRepository).saveAll(consumersCaptor.capture());
        List<Consumer> saved = consumersCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getIdentifier()).isEqualTo("instance-1_OrderObserver_consumer");
        assertThat(saved.getFirst().getComponent()).isSameAs(component);
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingComponentsAreReusedWithoutRecreatingAnything() {
        var projector = new Component();
        projector.setComponentName("OrderProjector");
        projector.setComponentType(ComponentType.Projector);
        projector.setUpdatedAt(Instant.now());
        when(componentRepository.findById("OrderProjector")).thenReturn(Optional.of(projector));

        service.registerConsumers("lab-bundle", "instance-1", 3,
                registration(byComponent("OrderProjector", "c1", "c2"),
                        new HashMap<>(), new HashMap<>()));

        verify(componentRepository, never()).save(any());
        verify(bundleRepository, never()).save(any());
        var consumersCaptor = ArgumentCaptor.forClass(List.class);
        verify(consumerRepository).saveAll(consumersCaptor.capture());
        List<Consumer> saved = consumersCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(c -> assertThat(c.getComponent()).isSameAs(projector));
        assertThat(saved).extracting(Consumer::getIdentifier)
                .containsExactlyInAnyOrder("instance-1_c1", "instance-1_c2");
    }
}
