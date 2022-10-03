package org.eventrails.server.controller;

import org.eventrails.parser.model.RanchApplicationDescription;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@Controller("/api/clique")
public class CliqueController {

	@PostMapping("/register")
	public void registerClique(@RequestBody RanchApplicationDescription description,
							   MultipartFile artifact){

	}
}
