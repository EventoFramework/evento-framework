package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentQuery;
import com.evento.common.messaging.gateway.QueryGateway;

/**
 * Handler directly calls queryGateway.query — verifies Query detection.
 */
public class QueryCallerFixture {

    private QueryGateway queryGateway;

    public void onEvent(String event) {
        queryGateway.query(new SentQuery());
    }
}
