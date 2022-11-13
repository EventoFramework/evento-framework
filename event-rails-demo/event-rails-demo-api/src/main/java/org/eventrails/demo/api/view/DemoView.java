package org.eventrails.demo.api.view;

import org.eventrails.common.modeling.messaging.payload.View;

public class DemoView extends View {
	private String demoId;
	private String name;
	private Long value;

	public DemoView(String demoId, String name, long value) {
		this.demoId = demoId;
		this.name = name;
		this.value = value;
	}

	public DemoView() {
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
}
