package org.eventrails.modeling.gateway;

import org.eventrails.modeling.messaging.payload.Query;
import org.eventrails.modeling.messaging.payload.View;
import org.eventrails.modeling.messaging.query.QueryResponse;

import java.util.concurrent.CompletableFuture;

public interface QueryGateway {
	<T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query);
}
