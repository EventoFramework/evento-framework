package com.evento.lab.streaming;

import com.evento.application.performance.Track;
import com.evento.application.performance.TracingAgent;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.QueryMessage;

import java.util.concurrent.atomic.AtomicInteger;

/** Counts the queries the bundle's handlers run under tracking: the hook a real tracer uses. */
public class CountingTracingAgent extends TracingAgent {

    public final AtomicInteger trackedQueries = new AtomicInteger();

    public CountingTracingAgent(String bundleId) {
        super(bundleId, 1);
    }

    @Override
    protected <T> T doTrack(Message<?> message, String component, Track trackingAnnotation,
                            Transaction<T> transaction) throws Throwable {
        if (message instanceof QueryMessage<?>) trackedQueries.incrementAndGet();
        return super.doTrack(message, component, trackingAnnotation, transaction);
    }
}
