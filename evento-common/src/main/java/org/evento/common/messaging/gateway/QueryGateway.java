package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;

import java.util.concurrent.CompletableFuture;

public interface QueryGateway {
	@SuppressWarnings("unchecked")
	<T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query);
}
