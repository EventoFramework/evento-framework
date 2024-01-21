package org.evento.demo.api.query;

import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.demo.api.view.NotificationView;

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
