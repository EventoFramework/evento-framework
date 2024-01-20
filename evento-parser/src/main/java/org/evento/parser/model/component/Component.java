package org.evento.parser.model.component;

import java.io.Serializable;

/**
 * The {@code Component} class is an abstract class that represents a generic component.
 * It provides basic properties like componentName, description, detail, path and line along with their getter and setter methods.
 * ...
 * This class is meant to be extended by other specific component classes such as Invoker, Projection, Projector, etc.
 * It can be used as follows:
 * <pre>
 *     Component comp = new {@code Component}();
 *     comp.setComponentName("Component1");
 *     comp.setDescription("This is a sample component.");
 *     comp.setDetail("Detail of the component.");
 *     comp.setPath("/path/to/component");
 *     comp.setLine(10);
 * </pre>
 */
public abstract class Component implements Serializable {

	/**
	 * The name of the component.
	 */
	private String componentName;

	/**
	 * A description of the component.
	 */
	private String description;

	/**
	 * Additional details about the component.
	 */
	private String detail;

	/**
	 * The path where the component is located.
	 */
	private String path;

	/**
	 * The line number in the source code where the component is defined.
	 */
	private int line;

	/**
	 * Gets the component name.
	 *
	 * @return a string for the component name.
	 */
	public String getComponentName() {
		return componentName;
	}

	/**
	 * Sets the component name.
	 *
	 * @param componentName the new component name.
	 */
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	/**
	 * Gets the description for the component.
	 *
	 * @return a string for the component description.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Sets the description for the component.
	 *
	 * @param description the new component description.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Gets the detail for the component.
	 *
	 * @return a string for the component detail.
	 */
	public String getDetail() {
		return detail;
	}

	/**
	 * Sets the detail for the component.
	 *
	 * @param detail the new component detail.
	 */
	public void setDetail(String detail) {
		this.detail = detail;
	}

	/**
	 * Gets the path for the component.
	 *
	 * @return a string for the component path.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Sets the path for the component.
	 *
	 * @param path the new component path.
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * Gets the line number for the component.
	 *
	 * @return an int for the component line.
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Sets the line number for the component.
	 *
	 * @param line the new component line.
	 */
	public void setLine(int line) {
		this.line = line;
	}
}
