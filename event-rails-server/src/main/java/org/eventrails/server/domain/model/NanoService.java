package org.eventrails.server.domain.model;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class NanoService {

	@Id
	private String name;
	private BucketType bucketType;
	private String artifactCoordinates;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
		NanoService that = (NanoService) o;
		return name != null && Objects.equals(name, that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}
}
