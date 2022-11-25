package org.eventrails.server.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.model.Handler;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

	private boolean autorun;
	private int minInstances;
	private int maxInstances;

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
	}
}