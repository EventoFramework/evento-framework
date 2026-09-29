package com.evento.server.domain.model.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import com.evento.common.modeling.bundle.types.ComponentType;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * This class represents a Component in the system
 * Every component has a name, belongs to a bundle
 * and has defined type, descriptions and location.
 *
 * <p><b>Identity is per bundle.</b> A component is identified by
 * {@code (bundle, componentName)}, stored as the deterministic key {@link #idOf}. The
 * component name is a simple class name and the same class is routinely deployed in
 * several bundles (one projector class run by a {@code query-<region>} bundle per
 * region, for example). Keying rows by the name alone made those bundles overwrite
 * each other's row, and one bundle's unregister deleted the handler and consumer rows
 * of every other bundle sharing the class.
 */
@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "core__component")
@Entity
public class Component {

    /**
     * Catalog key of the component: {@link #idOf(String, String)} of its bundle and name.
     * Handler and consumer rows reference it ({@code component_id}).
     */
	@Id
	private String id;

    /**
     * Represents the name of the component (its simple class name). Unique only within
     * a bundle.
     */
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
    @Column(columnDefinition = "TEXT")
	private String path;

    /**
     * Represents the line number where the component can be found.
     */
	private Integer line;

    /**
     * The catalog key of component {@code componentName} of bundle {@code bundleId}.
     * Bundle ids and Java simple class names never contain {@code ':'}, so the key is
     * unambiguous. A null bundle (rows migrated from an older catalog that had none)
     * maps to the empty string, as the schema migration does.
     */
    public static String idOf(String bundleId, String componentName) {
        return (bundleId == null ? "" : bundleId) + ":" + componentName;
    }
}
