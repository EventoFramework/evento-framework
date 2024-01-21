package org.evento.demo.agent;

import io.sentry.Sentry;
import io.sentry.protocol.User;
import org.evento.application.EventoBundle;
import org.evento.demo.agent.agents.DemoLifecycleAgent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

@SuppressWarnings("CallToPrintStackTrace")
@Component
public class AgentDispatcher implements CommandLineRunner {


	private final EventoBundle eventoBundle;

	public AgentDispatcher(EventoBundle eventoBundle) {
		this.eventoBundle = eventoBundle;
	}

	@Override
	public void run(String... args) throws Exception {


		var demoLifecycleAgent = eventoBundle.getInvoker(DemoLifecycleAgent.class);

		Thread.sleep(3000);


		for (int i = 0; i < 10; i++)
		{
			Thread.sleep(500);
			int finalI = i;
			new Thread(() -> {
				try
				{

					var user = new User();
					if(new Random().nextBoolean()) {
						user.setEmail("gabor.galazzo@gmail.com");
						user.setName("Gabor Galazzo");
						user.setId("123456");
						user.setUsername("gaborando");
						user.setData(Map.of("isAdmin", "true"));
					}else{
						user.setEmail("cenaturalmente@gmail.com");
						user.setName("Mariann Szilagyi");
						user.setId("654321");
						user.setUsername("cenaturalmente");
						user.setData(Map.of("isAdmin", "false"));
					}
					Sentry.setUser(user);
					demoLifecycleAgent.action(finalI);
					System.out.println("--------------------");
					System.out.println(Sentry.getLastEventId());
				} catch (Exception e)
				{
					e.printStackTrace();
				}
				System.out.println(finalI + "_end");
			}).start();
		}

    }
}
