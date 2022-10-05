package org.eventrails.modeling.messaging.query;

import org.eventrails.modeling.messaging.payload.View;

import java.util.Collection;
import java.util.List;

public class Multiple<T extends View> extends QueryResponse<T>{





	private Collection<T> data;

	public Multiple() {
	}

	public Collection<T> getData() {
		return data;
	}

	public void setData(Collection<T> data) {
		this.data = data;
	}

	public static <R extends View> Multiple<R> of(Collection<R> data) {
		var r =  new Multiple<R>();
		r.setData(data);
		return r;
	}

	public static <R extends View> Multiple<R > of(R... items) {
		var r =  new Multiple<R>();
		r.setData(List.of(items));
		return r;
	}

}
