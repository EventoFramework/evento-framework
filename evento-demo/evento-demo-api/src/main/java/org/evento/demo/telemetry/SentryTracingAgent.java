package org.evento.demo.telemetry;

import io.sentry.*;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import org.evento.application.performance.TracingAgent;
import org.evento.application.performance.Track;
import org.evento.common.modeling.messaging.message.application.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;

import static io.sentry.BaggageHeader.BAGGAGE_HEADER;
import static io.sentry.SentryTraceHeader.SENTRY_TRACE_HEADER;

public class SentryTracingAgent implements TracingAgent {

	public SentryTracingAgent(String sentryDns) {
		Sentry.init(options -> {
			options.setDsn(sentryDns);
			options.setEnableTracing(true);
			options.setDebug(true);
			options.setTracesSampleRate(1.0);
		});
	}

	@Override
	public <T> T track(Message<?> message, String component, String bundle, long bundleVersion,
					   Track trackingAnnotation,
					   Transaction<T> transaction) throws Throwable {
		var metadata = message.getMetadata();
		if (metadata == null)
		{
			metadata = new HashMap<>();
		}
		ITransaction t;
		var action = "invoke";
		if (message instanceof CommandMessage)
		{
			action = "handleCommand";
		}
		if (message instanceof QueryMessage)
		{
			action = "handleQuery";
		} else if (message instanceof EventMessage)
		{
			action = "onEvent";
		} else if (message instanceof InvocationMessage i)
		{
			action = i.getAction();
		}
		if (metadata.containsKey(SENTRY_TRACE_HEADER))
		{
			try
			{
				t = Sentry.startTransaction(
						TransactionContext.fromSentryTrace(
								component + "." + action + "(" + message.getPayloadName() + ")",
								"evento"
								, new SentryTraceHeader(
										metadata.get(SENTRY_TRACE_HEADER)
								)
						)
				);
			} catch (InvalidSentryTraceHeaderException e)
			{
				return transaction.run();

			}
		} else
		{
			if (trackingAnnotation != null)
			{

				t = Sentry.startTransaction(
						component + "." + action + "(" + message.getPayloadName() + ")",
						"evento");
			} else
			{
				return transaction.run();
			}
		}
		metadata.put(SENTRY_TRACE_HEADER, t.toSentryTrace().getValue());
		t.setData("Description", t.getName() + " - " + bundle + "@" + bundleVersion);
		t.setTag("message", message.getPayloadName());
		t.setTag("component", component);
		t.setTag("bundle", bundle);
		t.setTag("bundleVersion", String.valueOf(bundleVersion));
		if (message instanceof DomainCommandMessage cm)
		{
			t.setData("AggregateId", cm.getAggregateId());
		}
		message.setMetadata(metadata);
		try
		{
			var resp = transaction.run();
			t.finish(SpanStatus.OK);
			return resp;
		} catch (Throwable tr)
		{
			t.setThrowable(tr);
			t.setData("Pyload", message.getSerializedPayload().getSerializedObject());
			t.finish(SpanStatus.INTERNAL_ERROR);
			Sentry.captureException(tr);
			System.out.println(t.toSentryTrace().getTraceId());
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

	public static class TrackedException extends RuntimeException {

		private String traceRef;

		public TrackedException(ITransaction transaction, Throwable cause) {
			super(cause + ": " + cause.getMessage() + " (trace:" + transaction.toSentryTrace().getValue() + ")",
					cause, false, false);
			this.traceRef = transaction.toSentryTrace().getValue();
		}

		@Override
		public StackTraceElement[] getStackTrace() {
			return Stream.concat(Arrays.stream(super.getStackTrace()),
					Arrays.stream(getCause().getStackTrace())).toArray(StackTraceElement[]::new);
		}

		@Override
		public void printStackTrace() {
			super.printStackTrace();
		}

		public String getTraceRef() {
			return traceRef;
		}

		public void setTraceRef(String traceRef) {
			this.traceRef = traceRef;
		}
	}


}
