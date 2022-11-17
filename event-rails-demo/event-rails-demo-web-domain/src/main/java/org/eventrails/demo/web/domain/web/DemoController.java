package org.eventrails.demo.web.domain.web;

import org.eventrails.application.EventRailsApplication;
import org.eventrails.demo.api.command.DemoCreateCommand;
import org.eventrails.demo.api.command.DemoDeleteCommand;
import org.eventrails.demo.api.command.DemoUpdateCommand;
import org.eventrails.demo.api.query.DemoViewFindAllQuery;
import org.eventrails.demo.api.query.DemoViewFindByIdQuery;
import org.eventrails.demo.api.utils.Utils;
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

	public DemoController(EventRailsApplication eventRailsApplication) {
		this.commandGateway = eventRailsApplication.getCommandGateway();
		this.queryGateway = eventRailsApplication.getQueryGateway();
	}

	@GetMapping("/")
	@InvocationHandler
	public CompletableFuture<Collection<DemoView>> findAll(@RequestParam int page) {
		Utils.logMethodFlow(this,"findAll", page, "BEGIN");
		var resp =  queryGateway.query(new DemoViewFindAllQuery(10, page * 10)).thenApply(Multiple::getData);
		Utils.logMethodFlow(this,"findAll", page, "END");
		return resp;
	}

	@GetMapping("/{identifier}")
	@InvocationHandler
	public CompletableFuture<DemoView> findById(@PathVariable String identifier) {
		Utils.logMethodFlow(this,"findById", identifier, "BEGIN");
		var resp =  queryGateway.query(new DemoViewFindByIdQuery(identifier)).thenApply(Single::getData);
		Utils.logMethodFlow(this,"findById", identifier, "END");
		return resp;
	}

	@PostMapping("/")
	@InvocationHandler
	public CompletableFuture<?> save(@RequestBody DemoPayload demoPayload){
		Utils.logMethodFlow(this,"save", demoPayload, "BEGIN");
		return commandGateway.send(new DemoCreateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		)).thenApply(o -> {
			Utils.logMethodFlow(this,"save", demoPayload, "END");
			return o;
		});
	}

	@PutMapping("/{identifier}")
	@InvocationHandler
	public CompletableFuture<?> update(@RequestBody DemoPayload demoPayload, @PathVariable String identifier){
		Utils.logMethodFlow(this,"update", demoPayload, "BEGIN");
		demoPayload.setDemoId(identifier);
		return commandGateway.send(new DemoUpdateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		)).thenApply(o -> {
			Utils.logMethodFlow(this,"update", demoPayload, "END");
			return o;
		});
	}

	@DeleteMapping("/{identifier}")
	@InvocationHandler
	public CompletableFuture<?> delete(@PathVariable String identifier){
		Utils.logMethodFlow(this,"delete", identifier, "BEGIN");
		return commandGateway.send(new DemoDeleteCommand(identifier
		)).thenApply(o -> {
			Utils.logMethodFlow(this,"delete", identifier, "END");
			return o;
		});
	}
}
