package org.evento.common.modeling.messaging.query;

import org.evento.common.modeling.messaging.payload.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Multiple<T extends View> extends QueryResponse<T> {

	private Collection<T> data;

	public Multiple() {
	}

	public static <R extends View> Multiple<R> of(Collection<R> data) {
		var r = new Multiple<R>();
		r.setData(new ArrayList<>(data));
		return r;
	}

	@SafeVarargs
	public static <R extends View> Multiple<R> of(R... items) {
		var r = new Multiple<R>();
		r.setData(List.of(items));
		return r;
	}

	public Collection<T> getData() {
		return data;
	}

	public void setData(Collection<T> data) {
		this.data = data;
	}

}
