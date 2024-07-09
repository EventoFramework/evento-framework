package com.evento.common.performance;

import java.io.Serializable;

/**
 * The PerformanceServiceTimeMessage class represents a message object used to store service time metrics.
 *
 * <p>
 * The message contains the following properties:
 * - bundle: The bundle associated with the metric.
 * - component: The component associated with the metric.
 * - action: The action associated with the metric.
 * - start: The start time of the service.
 * - end: The end time of the service.
 * - instanceId: The node Id
 * </p>
 *
 * <p>
 * This class provides the following methods:
 * - Default constructor: Creates a new PerformanceServiceTimeMessage object with default property values.
 * - Parameterized constructor: Creates a new PerformanceServiceTimeMessage object with the specified property values.
 * - Getter and setter methods: Get and set the values of the bundle, component, action, start, and end properties.
 * </p>
 *
 */
public class PerformanceServiceTimeMessage implements Serializable {

	private String bundle;
	private String component;
	private String action;
	private long start;
	private long end;
	private String instanceId;

	/**
	 * The PerformanceServiceTimeMessage class represents a message object used to store service time metrics.
	 *
	 * <p>
	 * The message contains the following properties:
	 * - bundle: The bundle associated with the metric.
	 * - component: The component associated with the metric.
	 * - action: The action associated with the metric.
	 * - start: The start time of the service.
	 * - end: The end time of the service.
	 * </p>
	 *
	 * <p>
	 * This class provides the following methods:
	 * - Default constructor: Creates a new PerformanceServiceTimeMessage object with default property values.
	 * - Parameterized constructor: Creates a new PerformanceServiceTimeMessage object with the specified property values.
	 * - Getter and setter methods: Get and set the values of the bundle, component, action, start, and end properties.
	 * </p>
	 *
	 * @since 1.0
	 */
	public PerformanceServiceTimeMessage() {
	}

	/**
	 * Creates a new PerformanceServiceTimeMessage object with the specified property values.
	 *
	 * @param bundle    The bundle associated with the metric.
	 * @param component The component associated with the metric.
	 * @param action    The action associated with the metric.
	 * @param start     The start time of the service.
	 * @param end       The end time of the service.
	 * @param instanceId	  The node id associated to this message
	 */
	public PerformanceServiceTimeMessage(String bundle, String component, String action, long start, long end, String instanceId) {
		this.bundle = bundle;
		this.component = component;
		this.action = action;
		this.start = start;
		this.end = end;
		this.instanceId = instanceId;
	}

	/**
	 * Retrieves the bundle associated with the PerformanceServiceTimeMessage.
	 *
	 * @return The bundle associated with the PerformanceServiceTimeMessage.
	 */
	public String getBundle() {
		return bundle;
	}

	/**
	 * Sets the bundle associated with the PerformanceServiceTimeMessage.
	 *
	 * @param bundle The bundle associated with the PerformanceServiceTimeMessage.
	 */
	public void setBundle(String bundle) {
		this.bundle = bundle;
	}

	/**
	 * Retrieves the component associated with the PerformanceServiceTimeMessage.
	 *
	 * @return The component associated with the PerformanceServiceTimeMessage.
	 */
	public String getComponent() {
		return component;
	}

	/**
	 * Sets the component associated with the PerformanceServiceTimeMessage.
	 *
	 * @param component The component associated with the PerformanceServiceTimeMessage.
	 */
	public void setComponent(String component) {
		this.component = component;
	}

	/**
	 * Retrieves the action associated with the PerformanceServiceTimeMessage.
	 *
	 * @return The action associated with the PerformanceServiceTimeMessage.
	 */
	public String getAction() {
		return action;
	}

	/**
	 * Sets the action associated with the PerformanceServiceTimeMessage.
	 *
	 * @param action The action to be set for the PerformanceServiceTimeMessage.
	 */
	public void setAction(String action) {
		this.action = action;
	}


	/**
	 * Retrieves the start time of the service.
	 *
	 * @return The start time of the service.
	 */
	public long getStart() {
		return start;
	}

	/**
	 * Sets the start time of the service in a PerformanceServiceTimeMessage object.
	 *
	 * @param start The start time of the service.
	 */
	public void setStart(long start) {
		this.start = start;
	}

	/**
	 * Retrieves the end time of the service from a PerformanceServiceTimeMessage object.
	 *
	 * @return The end time of the service.
	 */
	public long getEnd() {
		return end;
	}

	/**
	 * Sets the end time of the service in a PerformanceServiceTimeMessage object.
	 *
	 * @param end The end time of the service.
	 */
	public void setEnd(long end) {
		this.end = end;
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
