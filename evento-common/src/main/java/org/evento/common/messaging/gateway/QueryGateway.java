package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public interface QueryGateway {
	@SuppressWarnings("unchecked")
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query){
		return query(query, null);
	}
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, HashMap<String, String> metadata){
		return query(query, metadata, null);
	}
	<T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, HashMap<String, String> metadata, Message<?> handledMessage);
}
