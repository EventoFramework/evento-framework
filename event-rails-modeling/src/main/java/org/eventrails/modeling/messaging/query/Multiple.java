package org.eventrails.modeling.messaging.query;

import java.util.Collection;
import java.util.List;

public class Multiple<T> {
	public static <R> Multiple<R> of(R... items) {
		return new Multiple<R>(List.of(items));
	}

	public static <R> Multiple<R> of(Collection<R> data) {
		return new Multiple<R>(data);
	}

	public Collection<T> getData() {
		return data;
	}

	private final Collection<T> data;

	public Multiple(Collection<T> data) {
		this.data = data;
	}
}
