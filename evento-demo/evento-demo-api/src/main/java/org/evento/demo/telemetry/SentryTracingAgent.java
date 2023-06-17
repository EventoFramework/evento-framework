package org.evento.demo.telemetry;

import io.sentry.*;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import org.evento.application.performance.TracingAgent;
import org.evento.common.modeling.messaging.message.application.*;

import java.util.HashMap;
import java.util.List;

import static io.sentry.BaggageHeader.BAGGAGE_HEADER;
import static io.sentry.SentryTraceHeader.SENTRY_TRACE_HEADER;

public class SentryTracingAgent implements TracingAgent {

	public SentryTracingAgent(String sentryDns) {
		Sentry.init(options -> {
			options.setDsn(sentryDns);
			options.setDebug(true);
			options.setEnableTracing(true);
			options.setTracesSampleRate(1.0);
		});
	}

    @Override
    public <T> T track(Message<?> message, String component, String bundle, long bundleVersion, Transaction<T> transaction) throws Throwable {
        if (message.getMetadata() == null)
        {
            message.setMetadata(new HashMap<>());
        }
        ITransaction t;
        if (message.getMetadata().containsKey(SENTRY_TRACE_HEADER))
        {
            try
            {
                t = Sentry.startTransaction(
                        TransactionContext.fromSentryTrace(
								component + "." + message.getPayloadName(),
                                "Evento." + message.getClass().getSimpleName()
                                , new SentryTraceHeader(
                                        message.getMetadata().get(SENTRY_TRACE_HEADER)
                                )
                        )
                );
            } catch (InvalidSentryTraceHeaderException e)
            {
                t = Sentry.startTransaction(
						component + "." + message.getPayloadName(),
                        "Evento." + message.getClass().getSimpleName());

            }
        }else{
            t = Sentry.startTransaction(
					component + "." + message.getPayloadName(),
                    "Evento." + message.getClass().getSimpleName());
        }
		message.getMetadata().put(SENTRY_TRACE_HEADER, t.toSentryTrace().getValue());
		t.setDescription(component + "("+message.getPayloadName()+") - " + bundle + "@" + bundleVersion);
		t.setTag("message", message.getPayloadName());
		t.setTag("component", component);
		t.setTag("bundle", bundle);
		t.setTag("bundleVersion", String.valueOf(bundleVersion));
        try{
            var resp = transaction.run();
            t.finish(SpanStatus.OK);
            return resp;
        }catch (Throwable tr){
            t.setThrowable(tr);
			t.setData("payload", message.getSerializedPayload().getSerializedObject());
            t.finish(SpanStatus.INTERNAL_ERROR);
            throw tr;
        }
    }
	@Override
	public HashMap<String, String> correlate(HashMap<String, String> metadata, Message<?> handledMessage) {
		if (handledMessage == null)
			return metadata;
		if (handledMessage.getMetadata() != null && handledMessage.getMetadata().containsKey(SENTRY_TRACE_HEADER))
		{
			if (metadata == null)
			{
				metadata = new HashMap<>();
			}
			metadata.put(SENTRY_TRACE_HEADER, handledMessage.getMetadata().get(SENTRY_TRACE_HEADER));
		}
		if (handledMessage.getMetadata() != null && handledMessage.getMetadata().containsKey(BAGGAGE_HEADER))
		{
			if (metadata == null)
			{
				metadata = new HashMap<>();
			}
			metadata.put(BAGGAGE_HEADER, handledMessage.getMetadata().get(BAGGAGE_HEADER));
		}
		return metadata;
	}


}
