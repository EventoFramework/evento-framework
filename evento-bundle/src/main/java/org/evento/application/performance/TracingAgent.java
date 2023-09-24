package org.evento.application.performance;

import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.Metadata;

public class TracingAgent {

    private final String bundleId;
    private final long bundleVersion;

    public TracingAgent(String bundleId, long bundleVersion) {
        this.bundleId = bundleId;
        this.bundleVersion = bundleVersion;
    }

    public void correlate(Message<?> from, Message<?> to) {
        to.setMetadata(correlate(to.getMetadata(), from));
    }

    public <T> T track(Message<?> message,
                       String component,
                       Track trackingAnnotation,
                       Transaction<T> transaction)
            throws Throwable {
        return transaction.run();
    }

    public Metadata correlate(Metadata metadata, Message<?> handledMessage) {
        return metadata;
    }


    public static interface Transaction<T> {
        public T run() throws Throwable;
    }

    public String getBundleId() {
        return bundleId;
    }

    public long getBundleVersion() {
        return bundleVersion;
    }
}
