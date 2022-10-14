package org.eventrails.modeling.messaging.message.bus;

import java.io.Serializable;

public class CorrelatedMessage implements Serializable {
		private Object payload;
		private String correlationId;

		private boolean isResponse;

		public CorrelatedMessage() {
		}

		public CorrelatedMessage(String correlationId, Object payload, boolean isResponse) {
			this.payload = payload;
			this.correlationId = correlationId;
			this.isResponse = isResponse;
		}

		public Object getPayload() {
			return payload;
		}

		public void setPayload(Object payload) {
			this.payload = payload;
		}

		public String getCorrelationId() {
			return correlationId;
		}

		public void setCorrelationId(String correlationId) {
			this.correlationId = correlationId;
		}

		public boolean isResponse() {
			return isResponse;
		}

		public void setResponse(boolean response) {
			isResponse = response;
		}

		public boolean isRequest() {
			return !isResponse;
		}
	}