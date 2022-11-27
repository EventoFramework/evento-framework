package org.evento.demo.api.query;

import org.evento.demo.api.view.DemoView;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.Multiple;

public class DemoViewFindAllQuery extends Query<Multiple<DemoView>> {
	private Integer limit;
	private Integer offset;



	public DemoViewFindAllQuery(int limit, int offset) {
		this.limit = limit;
		this.offset = offset;
	}

	public DemoViewFindAllQuery() {
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}
}
