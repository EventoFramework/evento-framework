package org.evento.server.web.dto;

import lombok.Data;
import org.evento.server.domain.repository.core.ComponentTypeCount;
import org.evento.server.domain.repository.core.PayloadTypeCount;

import java.util.List;

@Data
public class DashboardDTO {
	private String serverName;
	private String serverVersion;
	private Long payloadCount;
	private List<PayloadTypeCount> payloadCountByType;
	private Long componentCount;
	private List<ComponentTypeCount> componentCountByType;

	private Long bundleCount;
	private Long deployableBundleCount;

	private Long bundleInViewCount;
	private Integer nodeInViewCount;

	private Long eventCount;

	private Long aggregateCount;
	private Double eventPublicationFrequency;

}
