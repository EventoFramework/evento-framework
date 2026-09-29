package com.evento.lab.cluster.gateway.bundle;

/**
 * Marks the (empty) component package of bundle {@code order-gateway}. The
 * gateway hosts no handler and no consumer: it only sends commands and queries
 * through its bundle's gateways, from the HTTP controller, which lives outside
 * this package on purpose (the confinement check sweeps the component package).
 */
public final class GatewayBundle {

    private GatewayBundle() {
    }
}
