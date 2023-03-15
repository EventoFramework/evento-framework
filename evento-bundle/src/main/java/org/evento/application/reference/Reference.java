package org.evento.application.reference;

public abstract class Reference {

	private final Object ref;

	protected Reference(Object ref) {
		this.ref = ref;
	}

	public Object getRef() {
		return ref;
	}

	public String getComponentName(){
		return ref.getClass().getSimpleName();
	}



}
