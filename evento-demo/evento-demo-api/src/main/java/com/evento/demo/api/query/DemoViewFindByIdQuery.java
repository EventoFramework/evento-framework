package com.evento.demo.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.demo.api.view.DemoView;

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
