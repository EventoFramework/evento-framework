package org.eventrails.demo.web.domain.web.payload;

import lombok.Data;

@Data
public class DemoPayload {
	private String demoId;
	private String name;
	private Long value;
}
