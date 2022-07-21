package org.eventrails.server.controller;

import org.eventrails.parser.model.Application;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@Controller("/api/nano-service")
public class NanoServiceController {

	@PostMapping("/register")
	public void registerApplication(@RequestBody Application description,
									MultipartFile artifact){

	}
}
