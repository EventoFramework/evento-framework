package com.evento.server.domain.model.core;

/**
 * Enum representing the different types of buckets that can be used in the {@link Bundle} class.
 * The available bucket types are:
 * <p>
 * - LocalFilesystem: Represents a bucket stored in the local filesystem.
 * - LiveServer: Represents a bucket stored in a live server.
 * - Ephemeral: Represents an ephemeral bucket that is created and destroyed on demand.
 * - LibraryOnly: Represents a bucket that contains only library files.
 * <p>
 * BucketType is used as an attribute in the Bundle class to specify the type of bucket associated with the bundle.
 * <p>
 * This class should not be used directly, but rather through the Bundle class.
 */
public enum BucketType {
	/**
	 * The LocalFilesystem class represents a bucket stored in the local filesystem.
	 * It is used as an attribute in the Bundle class to specify the type of bucket associated with the bundle.
	 * This class should not be used directly, but rather through the Bundle class.
	 */
	LocalFilesystem,
	/**
	 * Represents a bucket stored in a live server.
	 * <p>
	 * The LiveServer class is used as an attribute in the Bundle class to specify the type
	 * of bucket associated with the bundle. It represents a bucket that is stored in a live server.
	 */
	LiveServer,
	/**
	 * The Ephemeral class represents an ephemeral bucket that is created and destroyed on demand.
	 * It is one of the bucket types used in the Bundle class to specify the type of bucket associated with the bundle.
	 * <p>
	 * In the Bundle class, the bucketType property can be set to Ephemeral to indicate that an ephemeral bucket should be used.
	 */
	Ephemeral,
	/**
	 * Represents a bucket that contains only library files.
	 * LibraryOnly is one of the bucket types used in the Bundle class to specify the type of bucket associated with the bundle.
	 * <p>
	 * This class should not be used directly, but rather through the Bundle class.
	 *
	 * @see Bundle
	 */
	LibraryOnly
}
