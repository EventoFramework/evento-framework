package org.evento.server.domain.model;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.Map;
import java.util.Objects;

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
	private int minInstances;
	private int maxInstances;

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
