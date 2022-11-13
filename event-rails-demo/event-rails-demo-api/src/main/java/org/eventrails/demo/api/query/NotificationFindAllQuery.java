package org.eventrails.demo.api.query;

import org.eventrails.common.modeling.messaging.payload.Query;

public class NotificationFindAllQuery extends Query {
	private Integer limit;
	private Integer offset;

	public NotificationFindAllQuery(int limit, int offset) {
		this.limit = limit;
		this.offset = offset;
	}

	public NotificationFindAllQuery() {
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
