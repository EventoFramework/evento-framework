package org.evento.application.performance;

import org.evento.common.modeling.messaging.message.application.Message;

import java.util.HashMap;

public interface TracingAgent {

	public default void correlate(Message<?> from, Message<?> to) {
		to.setMetadata(correlate(to.getMetadata(), from));
	}

	default <T> T track(Message<?> message, String component, String bundle, long bundleVersion,
						Track trackingAnnotation,
						Transaction<T> transaction)
			throws Throwable {
		return transaction.run();
	}

	default HashMap<String, String> correlate(HashMap<String, String> metadata, Message<?> handledMessage) {
		return metadata;
	}


	public static interface Transaction<T> {
		public T run() throws Throwable;
	}
}