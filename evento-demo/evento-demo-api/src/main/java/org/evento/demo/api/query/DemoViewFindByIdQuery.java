package org.evento.demo.api.query;

import org.evento.demo.api.view.DemoView;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.Single;

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
