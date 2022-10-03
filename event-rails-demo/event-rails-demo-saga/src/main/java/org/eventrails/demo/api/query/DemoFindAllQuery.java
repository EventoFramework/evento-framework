package org.eventrails.demo.api.query;

import org.eventrails.modeling.messaging.payload.Query;

public class DemoFindAllQuery extends Query {
	private Integer limit;
	private Integer offset;

	public DemoFindAllQuery(int limit, int offset) {
		this.limit = limit;
		this.offset = offset;
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
