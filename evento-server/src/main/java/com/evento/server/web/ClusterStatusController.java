package com.evento.server.web;

import com.evento.server.bus.BusFacade;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.server.domain.model.core.BucketType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.service.BundleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The ClusterStatusController class is a REST controller that handles requests related to cluster status.
 * It provides endpoints for fetching cluster information and performing actions on the cluster.
 */
@RestController
@RequestMapping("api/cluster-status")
public class ClusterStatusController {

	private final Logger logger = LoggerFactory.getLogger(ClusterStatusController.class);
	private final BusFacade busFacade;
	private final BundleService bundleService;

	public ClusterStatusController(BusFacade busFacade, BundleService bundleService) {
		this.busFacade = busFacade;
		this.bundleService = bundleService;
	}

	@GetMapping(value = "/attended-view")
	@Secured("ROLE_WEB")
	public ResponseEntity<List<String>> findAllNodes() {
		var nodes = bundleService.findAllBundles().stream().filter(Bundle::isContainsHandlers)
				.filter(b -> b.getBucketType() != BucketType.Ephemeral).map(Bundle::getId).collect(Collectors.toList());
		return ResponseEntity.ok(nodes);
	}

	/**
	 * SSE stream that emits the current full + available views, then a fresh
	 * snapshot on every cluster change. Migrated from v1's two
	 * {@code addViewListener} / {@code addAvailableViewListener} hooks to a
	 * single {@code BusFacade.subscribe} that pattern-matches on
	 * {@link BusEvent.ViewChanged} and {@link BusEvent.AvailableViewChanged}.
	 *
	 * <p>BusFacade has no remove-listener API by design, so the subscription
	 * gates itself with {@code AtomicBoolean closed} — once the emitter goes
	 * down, the lambda becomes a no-op. The lambda then stays referenced from
	 * the subscriber list for the lifetime of the bus, which is fine for the
	 * dashboard use case (handful of long-lived browser tabs, not a high-churn
	 * workload).
	 */
	@GetMapping(value = "/view")
	@Secured("ROLE_WEB")
	public SseEmitter handle() throws IOException {
		SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
		emitter.send(Map.of("type", "current", "view", busFacade.currentView()));
		emitter.send(Map.of("type", "available", "view", busFacade.currentAvailableView()));
		AtomicBoolean closed = new AtomicBoolean(false);
		emitter.onCompletion(() -> closed.set(true));
		emitter.onTimeout(() -> closed.set(true));
		emitter.onError(t -> closed.set(true));
		busFacade.subscribe(event -> {
			if (closed.get()) return;
			try {
				switch (event) {
					case BusEvent.ViewChanged v ->
							emitter.send(Map.of("type", "current", "view", v.view()));
					case BusEvent.AvailableViewChanged av ->
							emitter.send(Map.of("type", "available", "view", av.availableView()));
					default -> { /* ignore other event types */ }
				}
			} catch (Exception e) {
				closed.set(true);
				logger.debug("SSE send failed, marking subscription closed", e);
			}
		});
		return emitter;
	}

}
