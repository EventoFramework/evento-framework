package org.evento.server.domain.repository.core.projection;

import org.evento.server.domain.model.core.BucketType;

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
	 * Retrieves the value of the autorun property for the Bundle.
	 *
	 * @return The value of the autorun property as a Boolean. Returns true if the bundle should be automatically run, false otherwise.
	 */
	Boolean getAutorun();

	/**
	 * Retrieves the BucketType associated with the BundleListProjection.
	 * <p>
	 * The BucketType enum represents the different types of buckets that can be used in the Bundle class.
	 * The available bucket types are:
	 * - LocalFilesystem: Represents a bucket stored in the local filesystem.
	 * - LiveServer: Represents a bucket stored in a live server.
	 * - Ephemeral: Represents an ephemeral bucket that is created and destroyed on demand.
	 * - LibraryOnly: Represents a bucket that contains only library files.
	 * <p>
	 * The bucketType property of the BundleListProjection class can be used to determine the type of bucket associated with the bundle.
	 *
	 * @return The BucketType associated with the BundleListProjection.
	 * @see BucketType
	 */
	BucketType getBucketType();

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
