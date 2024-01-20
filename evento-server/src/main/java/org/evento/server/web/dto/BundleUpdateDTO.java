package org.evento.server.web.dto;

import lombok.Data;

/**
 * Represents a DTO (Data Transfer Object) used for updating a bundle.
 */
@Data
public class BundleUpdateDTO {
	private String description;
	private String detail;
}
