package org.eventrails.application.reference;

public abstract class Reference {

	private final Object ref;

	protected Reference(Object ref) {
		this.ref = ref;
	}

	public Object getRef() {
		return ref;
	}



}
