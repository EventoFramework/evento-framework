package org.eventrails.modeling.messaging.query;

import org.eventrails.modeling.messaging.payload.View;

import java.util.Collection;
import java.util.List;

public class Single<T extends View> extends QueryResponse<T>{

	private T data;

	public Single() {
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public static <R extends View> Single<R> of(R data) {
		var r =  new Single<R>();
		r.setData(data);
		return r;
	}


}
