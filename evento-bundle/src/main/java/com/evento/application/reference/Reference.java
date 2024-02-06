package com.evento.application.reference;

import lombok.Getter;

/**
 * The Reference class is an abstract class that serves as a base class for managing references to objects.
 * It provides functionality for retrieving the component name of the referenced object.
 */
@Getter
public abstract class Reference {

	private final Object ref;

	protected Reference(Object ref) {
		this.ref = ref;
	}

	/**
	 * Retrieves the component name of the referenced object.
	 * @return the component name of the referenced object
	 */
	public String getComponentName() {
		return ref.getClass().getSimpleName();
	}


}
