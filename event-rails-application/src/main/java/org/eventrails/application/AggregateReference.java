package org.eventrails.application;


import org.eventrails.modeling.annotations.handler.EventSourcingHandler;

import java.lang.reflect.Method;
import java.util.HashMap;

public class AggregateReference {
	public Object getRef() {
		return ref;
	}

	private final Object ref;

	private HashMap<String, Method> eventSourcingReference = new HashMap<>();

	public AggregateReference(Object ref) {
		this.ref = ref;
		for (Method declaredMethod : ref.getClass().getDeclaredMethods())
		{
			var esh = declaredMethod.getAnnotation(EventSourcingHandler.class);
			if(esh != null){
				eventSourcingReference.put(declaredMethod.getParameters()[0].getClass().getSimpleName(), declaredMethod);
			}
		}
	}

	public Method getEventSourcingHandler(String eventName){
		return eventSourcingReference.get(eventName);
	}
}
