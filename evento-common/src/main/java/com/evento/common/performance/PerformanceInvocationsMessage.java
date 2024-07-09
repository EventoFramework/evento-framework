package com.evento.common.performance;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Class representing a performance invocations message.
 */
public class PerformanceInvocationsMessage implements Serializable {

	private String bundle;
	private String component;
	private String action;
	private HashMap<String, Integer> invocations;
	private String instanceId;


	/**
	 * Constructs a new PerformanceInvocationsMessage object.
	 * <p>
	 * This constructor creates a PerformanceInvocationsMessage object with all fields set to their default values.
	 * The default values are:
	 * - bundle: null
	 * - component: null
	 * - action: null
	 * - invocations: an empty HashMap
	 */
	public PerformanceInvocationsMessage() {
	}

	/**
	 * Constructs a PerformanceInvocationsMessage object with the specified bundle, component, action, and invocations.
	 *
	 * @param bundle      The bundle associated with the performance invocations message.
	 * @param component   The component associated with the performance invocations message.
	 * @param action      The action associated with the performance invocations message.
	 * @param invocations The invocations associated with the performance invocations message.
	 * @param instanceId	  The node id associated to this message
	 */
	public PerformanceInvocationsMessage(String bundle, String component, String action, HashMap<String, Integer> invocations,
										 String instanceId) {
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		this.invocations = invocations;
		this.instanceId = instanceId;
	}

	/**
	 * Retrieves the bundle associated with the performance invocations message.
	 *
	 * @return The bundle associated with the performance invocations message.
	 */
	public String getBundle() {
		return bundle;
	}

	/**
	 * Sets the bundle associated with the performance invocations message.
	 *
	 * @param bundle The new value for the bundle.
	 */
	public void setBundle(String bundle) {
		this.bundle = bundle;
	}

	/**
	 * Retrieves the component associated with the performance invocations message.
	 *
	 * @return The component associated with the performance invocations message.
	 */
	public String getComponent() {
		return component;
	}

	/**
	 * Sets the component associated with the performance invocations message.
	 *
	 * @param component The new value for the component.
	 */
	public void setComponent(String component) {
		this.component = component;
	}

	/**
	 * Retrieves the action associated with the performance invocations message.
	 *
	 * @return The action associated with the performance invocations message.
	 */
	public String getAction() {
		return action;
	}

	/**
	 * Sets the action associated with the performance invocations message.
	 * <p>
	 * This method sets the value of the action field in the PerformanceInvocationsMessage object.
	 *
	 * @param action The new value for the action.
	 */
	public void setAction(String action) {
		this.action = action;
	}

	/**
	 * Retrieves the invocations associated with the performance invocations message.
	 *
	 * @return The invocations associated with the performance invocations message.
	 */
	public HashMap<String, Integer> getInvocations() {
		return invocations;
	}

	/**
	 * Sets the invocations associated with the performance invocations message.
	 *
	 * @param invocations The new invocations to be associated with the performance invocations message.
	 */
	public void setInvocations(HashMap<String, Integer> invocations) {
		this.invocations = invocations;
	}

	/**
	 * Retrieves the node ID associated with the performance invocations message.
	 *
	 * @return The node ID associated with the performance invocations message.
	 */
	public String getInstanceId() {
		return instanceId;
	}

	/**
	 * Sets the node ID associated with the performance invocations message.
	 *
	 * @param instanceId The new value for the node ID.
	 */
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
}
