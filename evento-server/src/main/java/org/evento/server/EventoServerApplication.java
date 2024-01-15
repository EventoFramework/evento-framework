package org.evento.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventoServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventoServerApplication.class, args);
	}

}
