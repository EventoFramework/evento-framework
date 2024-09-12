package com.evento.server.domain.model.core;

import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The Bundle class represents a bundle in the system.
 * A bundle is a collection of related components and resources that can be deployed and executed together.
 * <p>
 * Bundles have the following properties:
 * - id: The unique identifier of the bundle.
 * - version: The version number of the bundle.
 * - description: The description of the bundle.
 * - detail: Additional detail about the bundle.
 * - bucketType: The type of bucket associated with the bundle.
 * - artifactCoordinates: The coordinates of the artifact associated with the bundle.
 * - artifactOriginalName: The original name of the artifact associated with the bundle.
 * - containsHandlers: Indicates whether the bundle contains handlers.
 * - environment: A map of environment variables for the bundle.
 * - vmOptions: A map of VM options for the bundle.
 * - autorun: Indicates whether the bundle should be automatically run.
 * - minInstances: The minimum number of instances of the bundle.
 * - maxInstances: The maximum number of instances of the bundle.
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


	@Enumerated(EnumType.STRING)
	private BucketType bucketType;
	private String artifactCoordinates;
	private String artifactOriginalName;
	private boolean containsHandlers;
	@ElementCollection
	@JoinTable(name = "core__bundle__environment")
	private Map<String, String> environment;
	@ElementCollection
	@JoinTable(name = "core__bundle__vm_option")
	private Map<String, String> vmOptions;
	private boolean autorun;
	private boolean deployable;
	private int minInstances;
	private int maxInstances;

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
