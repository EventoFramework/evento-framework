package com.evento.demo.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.demo.api.view.DemoView;

public class DemoViewFindAllQuery implements Query<Multiple<DemoView>> {
	private String filter;
	private String sort;
	private Integer limit;
	private Integer offset;


	public DemoViewFindAllQuery(String filter, String sort, int limit, int offset) {
		this.filter = filter;
		this.sort = sort;
		this.limit = limit;
		this.offset = offset;
	}

	public DemoViewFindAllQuery( int limit, int offset) {
		this.filter = "";
		this.sort = "";
		this.limit = limit;
		this.offset = offset;
	}

	public DemoViewFindAllQuery() {
	}

	public String getFilter() {
		return filter;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public String getSort() {
		return sort;
	}

	public void setSort(String sort) {
		this.sort = sort;
	}

	public Integer getLimit() {
		return limit;
	}

	public void setLimit(Integer limit) {
		this.limit = limit;
	}

	public Integer getOffset() {
		return offset;
	}

	public void setOffset(Integer offset) {
		this.offset = offset;
	}
}
