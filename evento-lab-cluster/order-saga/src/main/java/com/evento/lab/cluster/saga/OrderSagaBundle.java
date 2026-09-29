package com.evento.lab.cluster.saga;

import com.evento.application.EventoBundle;
import com.evento.lab.cluster.saga.bundle.PaymentSaga;
import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.ContextSelection;

import java.util.List;
import java.util.Map;

/**
 * Bundle {@code order-saga}: one {@link PaymentSaga}, one engine per context of
 * {@code EVENTO_CONTEXTS}. Orders never carry the literal {@code ALL}
 * context, so {@code ALL} is dropped here ({@link ContextSelection#regional()})
 * and a deployment left with no context refuses to start rather than run a
 * saga that can never fire.
 */
public class OrderSagaBundle implements BundleDefinition {

    public static List<String> sagaContexts(ContextSelection contexts) {
        var regional = contexts.regional();
        if (regional.isEmpty()) {
            throw new IllegalStateException("EVENTO_CONTEXTS=" + contexts + " gives PaymentSaga no context: "
                    + "orders carry region contexts (or use *)");
        }
        return regional;
    }

    @Override
    public Class<?> anchor() {
        return PaymentSaga.class;
    }

    @Override
    public Map<Class<?>, Object> injectables() {
        return Map.of();
    }

    @Override
    public boolean usesConsumers() {
        return true;
    }

    @Override
    public Map<String, List<String>> consumerPlan(BundleDeployment d) {
        return Map.of(PaymentSaga.class.getSimpleName(), sagaContexts(d.requireContexts()));
    }

    @Override
    public void configure(EventoBundle.Builder builder, BundleDeployment d) {
        builder.setComponentContexts(PaymentSaga.class, sagaContexts(d.requireContexts()).toArray(String[]::new));
    }
}
