package com.evento.server.domain.model.core;

import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * The Bundle class represents a bundle in the system.
 * A bundle is a collection of related components and resources known to the server.
 * <p>
 * Bundles have the following properties:
 * - id: The unique identifier of the bundle.
 * - version: The version number of the bundle.
 * - description: The description of the bundle.
 * - detail: Additional detail about the bundle.
 * - instanceId: The id of the node instance that registered this catalog entry, used to
 *   reclaim the entry when that instance leaves the cluster.
 * - containsHandlers: Indicates whether the bundle contains handlers.
 * - updatedAt: The timestamp when the bundle was last updated.
 * <p>
 * The Bundle class can be used in conjunction with other classes such as Component to represent a bundle and its components.
 */
@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Table(name = "core__bundle")
@AllArgsConstructor
public class Bundle {

	@Id
	private String id;
	private long version;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(columnDefinition = "TEXT")
	private String detail;

	private String linePrefix;

	/** Base URL for the repository browser, used to build source links: {@code {repositoryUrl}/{path}#{linePrefix}{line}}. */
	@Column(columnDefinition = "TEXT")
	private String repositoryUrl;

	/** Id of the node instance that registered this catalog entry; used to reclaim it on node leave. */
	private String instanceId;
	private boolean containsHandlers;

	private Instant updatedAt;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
		Bundle that = (Bundle) o;
		return id != null && Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
