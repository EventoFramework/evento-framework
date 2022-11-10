package org.eventrails.server;

import org.eventrails.server.service.performance.ApplicationPetriNetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
