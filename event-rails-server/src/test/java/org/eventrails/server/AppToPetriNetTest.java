package org.eventrails.server;

import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.domain.model.Handler;
import org.eventrails.server.domain.model.Payload;
import org.eventrails.server.domain.model.types.HandlerType;
import org.eventrails.server.domain.model.types.PayloadType;
import org.eventrails.server.domain.repository.BundleRepository;
import org.eventrails.server.domain.repository.HandlerRepository;
import org.eventrails.server.service.performance.ApplicationPetriNetService;
import org.eventrails.server.service.performance.Post;
import org.eventrails.server.service.performance.Transition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest
@ActiveProfiles("local")
public class AppToPetriNetTest {

	@Autowired
	ApplicationPetriNetService service;
	@Test
	public void test() {
		var n = service.toPetriNet();
		System.out.println(n);
	}


}
