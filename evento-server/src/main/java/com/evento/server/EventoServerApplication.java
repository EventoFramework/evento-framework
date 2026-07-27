package com.evento.server;

import com.evento.common.utils.FatalErrors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventoServerApplication {

	public static void main(String[] args) {
		// A VirtualMachineError that kills a thread (e.g. an OOM in a Netty event
		// loop) leaves the broker as a zombie: the process stays up but can no
		// longer register channels, so the container restart policy never fires.
		// Escalate to a JVM halt so the supervisor restarts a clean process.
		// Complements -XX:+ExitOnOutOfMemoryError in the container entrypoint.
		var previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			FatalErrors.escalateIfFatal(throwable);
			if (previous != null) previous.uncaughtException(thread, throwable);
			else System.err.println("Exception in thread \"" + thread.getName() + "\" " + throwable);
		});
		SpringApplication.run(EventoServerApplication.class, args);
	}

}
