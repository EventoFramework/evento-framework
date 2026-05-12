package com.evento.demo.agent;

import com.evento.demo.agent.agents.StressAgent;
import com.evento.demo.api.utils.StressDB;
import com.evento.application.EventoBundle;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("CallToPrintStackTrace")
@Component
public class AgentDispatcher implements CommandLineRunner {


    private final StressDB stressDB;
	private final EventoBundle eventoBundle;

	public AgentDispatcher(StressDB stressDB, EventoBundle eventoBundle) {
        this.stressDB = stressDB;
        this.eventoBundle = eventoBundle;
	}

	@Override
	public void run(String... args) throws Exception {

        var stress = eventoBundle.getInvoker(StressAgent.class);

        var instances = 3000L;
        var identifier = stress.createService(instances, stressDB);
        System.out.println("Created stress with identifier: " + identifier);


        var futures = new ArrayList<CompletableFuture<?>>();
        for (long i = 0; i < instances; i++) {
            System.out.println("Calling stress with identifier: " + identifier + " and instance: " + i);
            futures.add(stress.callService(identifier,
                    i,
                     stressDB));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        /*

		var demoLifecycleAgent = eventoBundle.getInvoker(DemoLifecycleAgent.class);

		Thread.sleep(3000);

		var r = 1;
		var s = new Semaphore(0);
		for (int i = 0; i < r; i++)
		{
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
				s.release();
			}).start();
		}

		Thread.ofPlatform().start(() -> {
			for (int i = 0; i < r; i++) {
                try {
                    s.acquire();
					System.out.println(i + " acquired");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
			System.exit(0);
		});
*/

    }
}
