package com.evento.application.scanner.fixtures;

import com.evento.application.scanner.fixtures.payloads.SentDomainCmd;
import com.evento.application.scanner.fixtures.payloads.SentQuery;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.messaging.gateway.QueryOptions;

/**
 * Every way of sending a command or asking a query, beyond send/query: keyed sends,
 * tracked sends, queries with options, streamed queries.
 */
public class GatewayVariantsFixture {

    private CommandGateway commandGateway;
    private QueryGateway queryGateway;

    public void onKeyed(String event) {
        commandGateway.sendIdempotent(new SentDomainCmd(), "key-1");
    }

    public void onTracked(String event) {
        commandGateway.sendTracked(new SentDomainCmd());
    }

    public void onQueryWith(String event) {
        queryGateway.queryWith(new SentQuery(), QueryOptions.defaults().readYourWrites());
    }

    public void onStream(String event) {
        try (var views = queryGateway.stream(new SentQuery())) {
            views.forEach(v -> { });
        }
    }
}
