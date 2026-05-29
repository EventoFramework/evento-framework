package com.evento.server.domain.repository.core.projection;

/**
 * The BundleListProjection interface represents a projected view of Bundle objects.
 * It provides methods to retrieve various properties of a bundle.
 */
public interface BundleListProjection {
	/**
	 * Retrieves the id of the BundleListProjection.
	 *
	 * @return The id of the BundleListProjection.
	 */
	String getId();

	/**
	 * Retrieves the version of the BundleListProjection.
	 *
	 * @return The version of the BundleListProjection as an Integer.
	 */
	Integer getVersion();

	/**
	 * Retrieves the description of the bundle.
	 *
	 * @return The description of the bundle as a String.
	 */
	String getDescription();

	/**
	 * Retrieves the components of the Bundle.
	 *
	 * @return The components of the Bundle as a String.
	 */
	String getComponents();

	/**
	 * Retrieves the domains associated with the BundleListProjection.
	 * <p>
	 * The getDomains method is used to retrieve the domains associated with the BundleListProjection.
	 * Domains represent a set of related components and resources within a bundle that can be deployed and executed together.
	 * <p>
	 * This method returns the domains as a string.
	 *
	 * @return The domains associated with the BundleListProjection as a string.
	 */
	String getDomains();
}
