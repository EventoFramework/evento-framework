package com.evento.server.service.discovery;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
 * The recreate goes through {@link CatalogWriter}'s ON CONFLICT inserts, so it
 * cannot collide with a concurrent discovery on another server.
 */
class ConsumerServiceRegistrationTest {

    private ComponentRepository componentRepository;
    private ConsumerRepository consumerRepository;
    private CatalogWriter catalogWriter;
    private ConsumerService service;

    @BeforeEach
    void setUp() {
        componentRepository = mock(ComponentRepository.class);
        consumerRepository = mock(ConsumerRepository.class);
        catalogWriter = mock(CatalogWriter.class);
        // Null DataSource → PgDistributedLock stays JVM-local (embedded/test mode).
        service = new ConsumerService(componentRepository, consumerRepository,
                mock(BundleRepository.class), "test-server", null, catalogWriter, null);
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
        var recreated = new Component();
        recreated.setComponentName("OrderObserver");
        recreated.setComponentType(ComponentType.Observer);
        when(componentRepository.findById("OrderObserver"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(recreated));
        when(catalogWriter.insertBundleIfAbsent(anyString(), anyLong(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(true);
        when(catalogWriter.insertComponentIfAbsent(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        service.registerConsumers("lab-bundle", "instance-1", 3,
                registration(new HashMap<>(), new HashMap<>(),
                        byComponent("OrderObserver", "OrderObserver_consumer")));

        verify(catalogWriter).insertBundleIfAbsent("lab-bundle", 3, "", "", "", "", "instance-1", true);
        verify(catalogWriter).insertComponentIfAbsent("OrderObserver", ComponentType.Observer, "lab-bundle",
                null, null, null, null);

        var consumersCaptor = ArgumentCaptor.forClass(List.class);
        verify(catalogWriter).upsertConsumers(consumersCaptor.capture());
        List<Consumer> saved = consumersCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getIdentifier()).isEqualTo("instance-1_OrderObserver_consumer");
        assertThat(saved.getFirst().getComponent()).isSameAs(recreated);
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

        verify(catalogWriter, never()).insertComponentIfAbsent(any(), any(), any(), any(), any(), any(), any());
        verify(catalogWriter, never()).insertBundleIfAbsent(any(), anyLong(), any(), any(), any(), any(), any(), anyBoolean());
        verify(consumerRepository, never()).saveAll(any());
        var consumersCaptor = ArgumentCaptor.forClass(List.class);
        verify(catalogWriter).upsertConsumers(consumersCaptor.capture());
        List<Consumer> saved = consumersCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(c -> assertThat(c.getComponent()).isSameAs(projector));
        assertThat(saved).extracting(Consumer::getIdentifier)
                .containsExactlyInAnyOrder("instance-1_c1", "instance-1_c2");
    }
}
