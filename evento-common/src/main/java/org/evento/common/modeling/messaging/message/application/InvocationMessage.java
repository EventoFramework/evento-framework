package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Invocation;

import java.lang.reflect.Method;

public class InvocationMessage extends Message<Invocation> {

	private String name;
	private String componentName;
	private String action;

	public InvocationMessage(Class<?> invoker, Method method, Object[] args) {
		super(generatePayload(method, args));
		this.name = invoker.getSimpleName() + "::" + method.getName();
		this.componentName = invoker.getSimpleName();
		this.action = method.getName();

	}

	private static Invocation generatePayload(Method method, Object[] args) {
		var i = new Invocation();
		for (int j = 0; j < method.getParameters().length; j++)
		{
			var p = method.getParameters()[j];
			var a = args[j];
			i.getArguments().put(p.getName(), a);
		}
		return i;
	}

	@Override
	public String getPayloadName() {
		return "";
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getComponentName() {
		return componentName;
	}

	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}
}
