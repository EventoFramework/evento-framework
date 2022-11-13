package org.eventrails.demo.api.view;

import org.eventrails.common.modeling.messaging.payload.View;

import java.time.Instant;

public class DemoRichView extends View {

	private String demoId;
	private String name;
	private Long value;
	private Instant createdAt;
	private Instant updatedAt;

	public DemoRichView(String demoId, String name, long value, Instant createdAt, Instant updatedAt) {
		this.demoId = demoId;
		this.name = name;
		this.value = value;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public DemoRichView() {
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
