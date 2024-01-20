package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Invocation;

import java.lang.reflect.Method;

/**
 * The InvocationMessage class represents a message that encapsulates a method invocation.
 * It extends the Message class and stores the name, component name, and action of the invocation.
 */
public class InvocationMessage extends Message<Invocation> {

	private String name;
	private String componentName;
	private String action;

	/**
	 * The InvocationMessage class represents a message that encapsulates a method invocation.
	 * It extends the Message class and stores the name, component name, and action of the invocation.
	 *
	 * @param invoker The class that invokes the method.
	 * @param method  The method being invoked.
	 * @param args    The arguments for the method invocation.
	 */
	public InvocationMessage(Class<?> invoker, Method method, Object[] args) {
		super(generatePayload(method, args));
		this.name = invoker.getSimpleName() + "::" + method.getName();
		this.componentName = invoker.getSimpleName();
		this.action = method.getName();

	}

	/**
	 * Generates an Invocation object with the method arguments.
	 *
	 * @param method The method being invoked.
	 * @param args   The arguments for the method invocation.
	 * @return The Invocation object containing the method arguments.
	 */
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

	/**
	 * Retrieves the name of the payload.
	 *
	 * @return the name of the payload
	 */
	@Override
	public String getPayloadName() {
		return name;
	}

	/**
	 * Retrieves the name of the payload.
	 *
	 * @return the name of the payload
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of the invocation message.
	 *
	 * @param name the name to be set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Retrieves the name of the component.
	 *
	 * @return the name of the component
	 */
	public String getComponentName() {
		return componentName;
	}

	/**
	 * Sets the name of the component.
	 *
	 * @param componentName the name to be set
	 */
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	/**
	 * Retrieves the action of the invocation message.
	 *
	 * @return the action of the invocation message
	 */
	public String getAction() {
		return action;
	}

	/**
	 * Sets the action of the InvocationMessage.
	 *
	 * @param action the action to be set
	 */
	public void setAction(String action) {
		this.action = action;
	}
}
