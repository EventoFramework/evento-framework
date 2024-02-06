package com.evento.server.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.evento.server.domain.model.core.BucketType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.domain.model.core.Handler;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A DTO for the {@link Bundle} entity
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BundleDto implements Serializable {
	private String id;

	private long version;
	private BucketType bucketType;
	private String artifactCoordinates;
	private String artifactOriginalName;
	private boolean containsHandlers;
	private Map<String, String> environment;
	private Map<String, String> vmOptions;
	private Collection<HandlerDto> handlers;

	private String detail;
	private String description;

	private boolean autorun;
	private int minInstances;
	private int maxInstances;

	private Instant updatedAt;
	private Set<String> domains;

	/**
	 * Creates a BundleDto object based on a Bundle and a list of Handlers.
	 *
	 * @param bundle   The Bundle object to create the BundleDto from.
	 * @param handlers The list of Handlers to include in the BundleDto.
	 */
	public BundleDto(Bundle bundle, List<Handler> handlers) {
		this.id = bundle.getId();
		this.version = bundle.getVersion();
		this.bucketType = bundle.getBucketType();
		this.artifactCoordinates = bundle.getArtifactCoordinates();
		this.artifactOriginalName = bundle.getArtifactOriginalName();
		this.containsHandlers = bundle.isContainsHandlers();
		this.environment = bundle.getEnvironment();
		this.vmOptions = bundle.getVmOptions();
		this.handlers = handlers.stream().map(HandlerDto::new).collect(Collectors.toList());
		this.autorun = bundle.isAutorun();
		this.minInstances = bundle.getMinInstances();
		this.maxInstances = bundle.getMaxInstances();
		this.description = bundle.getDescription();
		this.detail = bundle.getDetail();
		this.updatedAt = bundle.getUpdatedAt();
		this.domains = handlers.stream().map(h -> h.getHandledPayload().getDomain())
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}
}