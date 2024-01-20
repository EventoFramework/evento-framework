package org.evento.server.domain.repository.core.projection;

/**
 * Represents a view of the underlying Component data which shape is defined by the getter methods declared in this interface.
 */
public interface ComponentListProjection {

    /**
     * Returns the Component name.
     *
     * @return The component name.
     */
    String getComponentName();

    /**
     * Returns the type of the Component.
     *
     * @return The component type.
     */
    String getComponentType();

    /**
     * Returns the description of the Component.
     *
     * @return The description.
     */
    String getDescription();

    /**
     * Returns the domains of the Component.
     *
     * @return The domains.
     */
    String getDomains();

    /**
     * Returns the bundle ID of the Component.
     *
     * @return The bundle ID.
     */
    String getBundleId();

    /**
     * Returns the number of messages handled by the Component.
     *
     * @return The number of handled messages.
     */
    Integer getHandledMessages();

    /**
     * Returns the number of messages produced by the Component.
     *
     * @return The number of produced messages.
     */
    Integer getProducedMessages();

    /**
     * Returns the number of invocations made by or to the Component.
     *
     * @return The number of invocations.
     */
    Integer getInvocations();
}