package org.eventrails.server.controller;

import org.eventrails.modeling.sdk.ApplicationDescription;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@Controller("/api/nano-service")
public class NanoServiceController {

	@PostMapping("/register")
	public void registerApplication(@RequestBody ApplicationDescription description,
									MultipartFile artifact){

	}
}
