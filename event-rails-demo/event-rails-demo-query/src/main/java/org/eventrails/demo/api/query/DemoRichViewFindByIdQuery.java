package org.eventrails.demo.api.query;

import org.eventrails.demo.api.view.DemoRichView;
import org.eventrails.modeling.messaging.payload.Query;
import org.eventrails.modeling.messaging.payload.View;
import org.eventrails.modeling.messaging.query.Single;

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
