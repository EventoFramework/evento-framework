package org.evento.demo.web.domain.web;

import org.evento.application.EventoBundle;
import org.evento.demo.api.command.DemoCreateCommand;
import org.evento.demo.api.command.DemoDeleteCommand;
import org.evento.demo.api.command.DemoUpdateCommand;
import org.evento.demo.api.query.DemoViewFindAllQuery;
import org.evento.demo.api.query.DemoViewFindByIdQuery;
import org.evento.demo.api.utils.Utils;
import org.evento.demo.api.view.DemoView;
import org.evento.demo.web.domain.web.payload.DemoPayload;
import org.evento.common.modeling.annotations.component.Invoker;
import org.evento.common.modeling.annotations.handler.InvocationHandler;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.modeling.messaging.query.Single;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
	private final DemoInvoker demoInvoker;

	public DemoController(EventoBundle eventoBundle) {
		this.demoInvoker = eventoBundle.getInvoker(DemoInvoker.class);
	}

	@GetMapping("/")
	public CompletableFuture<Collection<DemoView>> findAll(@RequestParam int page) {
		return demoInvoker.findAll(page);
	}

	@GetMapping("/{identifier}")
	public CompletableFuture<DemoView> findById(@PathVariable String identifier) {
		return demoInvoker.findById(identifier);
	}

	@PostMapping("/")
	public CompletableFuture<?> save(@RequestBody DemoPayload demoPayload) {
		return demoInvoker.save(demoPayload);
	}

	@PutMapping("/{identifier}")
	public CompletableFuture<?> update(@RequestBody DemoPayload demoPayload, @PathVariable String identifier) {
		return demoInvoker.update(demoPayload, identifier);
	}

	@DeleteMapping("/{identifier}")
	public CompletableFuture<?> delete(@PathVariable String identifier) {
		return demoInvoker.delete(identifier);
	}
}
