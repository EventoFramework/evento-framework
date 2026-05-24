package com.evento.common.messaging.consumer;

import com.evento.common.messaging.consumer.impl.InMemorySagaStateStore;
import com.evento.common.modeling.state.SagaState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySagaStateStoreTest {

    static final class TestSaga extends SagaState {}

    @Test
    void insertAssignsMonotonicIds() {
        var store = new InMemorySagaStateStore();
        long a = store.insert("Order", new TestSaga());
        long b = store.insert("Order", new TestSaga());
        assertThat(b).isGreaterThan(a);
    }

    @Test
    void findByAssociationMatchesOnPropertyValue() {
        var store = new InMemorySagaStateStore();
        var s1 = new TestSaga();
        s1.setAssociation("orderId", "ord-1");
        var s2 = new TestSaga();
        s2.setAssociation("orderId", "ord-2");
        long id1 = store.insert("Order", s1);
        store.insert("Order", s2);

        var found = store.findByAssociation("Order", "orderId", "ord-1").orElseThrow();
        assertThat(found.getId()).isEqualTo(id1);
        assertThat(found.getState()).isSameAs(s1);

        assertThat(store.findByAssociation("Order", "orderId", "missing")).isEmpty();
        assertThat(store.findByAssociation("Other", "orderId", "ord-1")).isEmpty();
    }

    @Test
    void findAllReturnsAllInstancesOfSagaName() {
        var store = new InMemorySagaStateStore();
        store.insert("Order", new TestSaga());
        store.insert("Order", new TestSaga());
        store.insert("Refund", new TestSaga());

        assertThat(store.findAll("Order")).hasSize(2);
        assertThat(store.findAll("Refund")).hasSize(1);
        assertThat(store.findAll("None")).isEmpty();
    }

    @Test
    void updateReplacesStateUnderSameId() {
        var store = new InMemorySagaStateStore();
        var s1 = new TestSaga();
        s1.setAssociation("k", "v1");
        long id = store.insert("S", s1);

        var s2 = new TestSaga();
        s2.setAssociation("k", "v2");
        store.update(id, s2);

        assertThat(store.findByAssociation("S", "k", "v1")).isEmpty();
        assertThat(store.findByAssociation("S", "k", "v2").orElseThrow().getId()).isEqualTo(id);
    }

    @Test
    void deleteRemovesInstance() {
        var store = new InMemorySagaStateStore();
        var s = new TestSaga();
        s.setAssociation("k", "v");
        long id = store.insert("S", s);
        store.delete(id);
        assertThat(store.findByAssociation("S", "k", "v")).isEmpty();
        assertThat(store.findAll("S")).isEmpty();
    }
}
