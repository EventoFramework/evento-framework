package org.evento.server.domain.model.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.evento.common.modeling.bundle.types.ComponentType;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * This class represents a Component in the system
 * Every component has a name, belongs to a bundle
 * and has defined type, descriptions and location.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "core__component")
@Entity
public class Component {

    /**
     * Represents the name of the component.
     */
	@Id
	private String componentName;

    /**
     * Represents the associated bundle of the component.
     */
	@ManyToOne()
	private Bundle bundle;

    /**
     * Represents the type of the component.
     */
	@Enumerated(EnumType.STRING)
	private ComponentType componentType;

    /**
     * Brief description of the component.
     */
	@Column(columnDefinition = "TEXT")
	private String description;

    /**
     * Detailed description of the component.
     */
	@Column(columnDefinition = "TEXT")
	private String detail;

    /**
     * Represents the last updated timestamp of the component.
     */
	private Instant updatedAt;

    /**
     * Represents the path where the component resides.
     */
	private String path;

    /**
     * Represents the line number where the component can be found.
     */
	private Integer line;
}