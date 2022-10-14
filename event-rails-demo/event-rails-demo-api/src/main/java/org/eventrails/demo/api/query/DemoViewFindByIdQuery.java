package org.eventrails.demo.api.query;

import org.eventrails.demo.api.view.DemoView;
import org.eventrails.modeling.messaging.payload.Query;
import org.eventrails.modeling.messaging.query.Single;

public class DemoViewFindByIdQuery extends Query<Single<DemoView>> {
	private String demoId;

	public DemoViewFindByIdQuery(String demoId) {
		this.demoId = demoId;
	}

	public DemoViewFindByIdQuery() {
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}
}
