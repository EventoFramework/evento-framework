package org.evento.application.performance;

import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.Metadata;

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

	default Metadata correlate(Metadata metadata, Message<?> handledMessage) {
		return metadata;
	}


	public static interface Transaction<T> {
		public T run() throws Throwable;
	}
}
