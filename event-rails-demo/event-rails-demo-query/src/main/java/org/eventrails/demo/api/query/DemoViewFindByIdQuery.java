package org.eventrails.demo.api.query;

import org.eventrails.modeling.messaging.payload.Query;

public class DemoViewFindByIdQuery extends Query {
	private String demoId;

	public DemoViewFindByIdQuery(String demoId) {
		this.demoId = demoId;
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}
}
