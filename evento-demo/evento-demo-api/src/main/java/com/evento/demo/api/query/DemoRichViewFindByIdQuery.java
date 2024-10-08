package com.evento.demo.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.demo.api.view.DemoRichView;

public class DemoRichViewFindByIdQuery extends Query<Single<DemoRichView>> {
	private String demoId;


	public DemoRichViewFindByIdQuery(String demoId) {
		this.demoId = demoId;
	}

	public DemoRichViewFindByIdQuery() {
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}
}
