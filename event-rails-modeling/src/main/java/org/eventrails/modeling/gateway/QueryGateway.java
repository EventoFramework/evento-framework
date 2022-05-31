package org.eventrails.modeling.gateway;

import org.eventrails.modeling.messaging.payload.Query;

import java.util.concurrent.CompletableFuture;

public interface QueryGateway {
	<T> CompletableFuture<T> query(Query query, Class<T> responseType);
}
