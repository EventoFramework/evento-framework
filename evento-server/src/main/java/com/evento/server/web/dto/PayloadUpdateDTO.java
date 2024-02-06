package com.evento.server.web.dto;

import lombok.Data;

@Data
public class PayloadUpdateDTO {
	private String description;
	private String detail;

	private String domain;
}
