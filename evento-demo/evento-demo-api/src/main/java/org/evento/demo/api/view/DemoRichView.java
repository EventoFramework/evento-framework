package org.evento.demo.api.view;

import org.evento.common.modeling.messaging.payload.View;

import java.time.Instant;

public class DemoRichView extends View {

	private String demoId;
	private String name;
	private Long value;
	private long createdAt;
	private long updatedAt;
	private Long deletedAt;

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

	public void setValue(Long value) {
		this.value = value;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}

	public long getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(long updatedAt) {
		this.updatedAt = updatedAt;
	}

	public long getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Long deletedAt) {
		this.deletedAt = deletedAt;
	}

	@Override
	public String toString() {
		return "DemoRichView{" +
				"demoId='" + demoId + '\'' +
				", name='" + name + '\'' +
				", value=" + value +
				", createdAt=" + createdAt +
				", updatedAt=" + updatedAt +
				", deletedAt=" + deletedAt +
				'}';
	}
}
