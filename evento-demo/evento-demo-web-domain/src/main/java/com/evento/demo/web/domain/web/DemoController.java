package com.evento.demo.web.domain.web;

import com.evento.application.EventoBundle;
import com.evento.demo.api.utils.Utils;
import com.evento.demo.api.view.DemoView;
import com.evento.demo.web.domain.web.payload.DemoPayload;
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
		Utils.waitForConsistency(1000);
		return demoInvoker.findAll(page);
	}

	@GetMapping("/{identifier}")
	public CompletableFuture<DemoView> findById(@PathVariable String identifier) {
		Utils.waitForConsistency(1000);
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
