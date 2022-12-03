package org.evento.demo.agent;

import org.evento.application.EventoApplication;
import org.evento.demo.agent.agents.DemoLifecycleAgent;
import org.evento.demo.agent.agents.Report;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.stream.IntStream;

@Component
public class AgentDispatcher implements CommandLineRunner {


	private final EventoApplication eventoApplication;

	public AgentDispatcher(EventoApplication eventoApplication) {
		this.eventoApplication = eventoApplication;
	}

	@Override
	public void run(String... args) throws Exception {


		var demoLifecycleAgent = new DemoLifecycleAgent(eventoApplication);


		var listStart = IntStream.range(0, 3).mapToObj(demoLifecycleAgent::action).toList();
		System.out.println("listStart (10) MeanCreateTime: "+listStart.stream().filter(Report::success).mapToLong(Report::createTime).average().orElse(0));
		System.out.println("listStart (10) MeanMeanUpdateTime: "+listStart.stream().filter(Report::success).mapToLong(Report::meanUpdateTime).average().orElse(0));
		System.out.println("listStart (10) MeanDeleteTime: "+listStart.stream().filter(Report::success).mapToLong(Report::deleteTime).average().orElse(0));
		listStart.stream().filter(r -> !r.success()).forEach(System.out::println);

		/*
		var listMiddle = IntStream.range(0, 30).mapToObj(demoLifecycleAgent::action).toList();
		var listEnd = IntStream.range(0, 10).mapToObj(demoLifecycleAgent::action).toList();
		System.out.println("listMiddle (30) MeanCreateTime: "+listMiddle.stream().filter(Report::success).mapToLong(Report::createTime).average().orElse(0));
		System.out.println("listMiddle (30) MeanMeanUpdateTime: "+listMiddle.stream().filter(Report::success).mapToLong(Report::meanUpdateTime).average().orElse(0));
		System.out.println("listMiddle (30) MeanDeleteTime: "+listMiddle.stream().filter(Report::success).mapToLong(Report::deleteTime).average().orElse(0));
		System.out.println("listEnd (10) MeanCreateTime: "+listEnd.stream().filter(Report::success).mapToLong(Report::createTime).average().orElse(0));
		System.out.println("listEnd (10) MeanMeanUpdateTime: "+listEnd.stream().filter(Report::success).mapToLong(Report::meanUpdateTime).average().orElse(0));
		System.out.println("listEnd (10) MeanDeleteTime: "+listEnd.stream().filter(Report::success).mapToLong(Report::deleteTime).average().orElse(0));

		listMiddle.stream().filter(r -> !r.success()).forEach(System.out::println);
		listEnd.stream().filter(r -> !r.success()).forEach(System.out::println);

		//eventoApplication.shutdown();
		*/
		eventoApplication.gracefulShutdown();

	}
}
