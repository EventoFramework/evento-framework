package org.eventrails.demo.api.query;

import org.eventrails.demo.api.view.NotificationView;
import org.eventrails.modeling.messaging.payload.Query;
import org.eventrails.modeling.messaging.payload.View;
import org.eventrails.modeling.messaging.query.Multiple;

public class NotificationFindAllQuery extends Query<Multiple<NotificationView>> {
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
