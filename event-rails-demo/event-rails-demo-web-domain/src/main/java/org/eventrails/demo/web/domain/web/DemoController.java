package org.eventrails.demo.web.domain.web;

import org.eventrails.demo.api.command.DemoCreateCommand;
import org.eventrails.demo.api.command.DemoDeleteCommand;
import org.eventrails.demo.api.command.DemoUpdateCommand;
import org.eventrails.demo.api.query.DemoViewFindAllQuery;
import org.eventrails.demo.api.query.DemoViewFindByIdQuery;
import org.eventrails.demo.api.view.DemoView;
import org.eventrails.demo.web.domain.web.payload.DemoPayload;
import org.eventrails.common.modeling.annotations.component.Invoker;
import org.eventrails.common.modeling.annotations.handler.InvocationHandler;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.messaging.query.Multiple;
import org.eventrails.common.modeling.messaging.query.Single;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/demo")
@Invoker
public class DemoController {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;

	public DemoController(CommandGateway commandGateway, QueryGateway queryGateway) {
		this.commandGateway = commandGateway;
		this.queryGateway = queryGateway;
	}

	@GetMapping("/")
	@InvocationHandler
	public CompletableFuture<Collection<DemoView>> findAll(@RequestParam int page) {
		return queryGateway.query(new DemoViewFindAllQuery(10, page * 10)).thenApply(Multiple::getData);
	}

	@GetMapping("/{identifier}")
	@InvocationHandler
	public CompletableFuture<DemoView> findById(@PathVariable String identifier) {
		return queryGateway.query(new DemoViewFindByIdQuery(identifier)).thenApply(Single::getData);
	}

	@PostMapping("/")
	@InvocationHandler
	public CompletableFuture<?> save(@RequestBody DemoPayload demoPayload){
		return commandGateway.send(new DemoCreateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		));
	}

	@PutMapping("/{identifier}")
	@InvocationHandler
	public CompletableFuture<?> update(@RequestBody DemoPayload demoPayload, @PathVariable String identifier){
		demoPayload.setDemoId(identifier);
		return commandGateway.send(new DemoUpdateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		));
	}

	@DeleteMapping("/{identifier}")
	@InvocationHandler
	public CompletableFuture<?> delete(@PathVariable String identifier){
		return commandGateway.send(new DemoDeleteCommand(identifier));
	}
}
