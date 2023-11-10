package org.evento.server.web;

import org.evento.server.bus.MessageBus;
import org.evento.server.bus.NodeAddress;
import org.evento.server.domain.model.core.BucketType;
import org.evento.server.domain.model.core.Bundle;
import org.evento.server.service.BundleService;
import org.evento.server.service.deploy.BundleDeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/cluster-status")
public class ClusterStatusController {

	private final Logger logger = LoggerFactory.getLogger(ClusterStatusController.class);
	private final MessageBus messageBus;
	private final BundleService bundleService;
	private final BundleDeployService bundleDeployService;
	@Value("${evento.cluster.node.server.id}")
	private String serverNodeName;

	public ClusterStatusController(MessageBus messageBus,
								   BundleService bundleService, BundleDeployService bundleDeployService) {
		this.messageBus = messageBus;
		this.bundleService = bundleService;
		this.bundleDeployService = bundleDeployService;
	}

	@GetMapping(value = "/attended-view")
	@Secured("ROLE_WEB")
	public ResponseEntity<List<String>> findAllNodes() {
		var nodes = bundleService.findAllBundles().stream().filter(Bundle::isContainsHandlers)
				.filter(b -> b.getBucketType() != BucketType.Ephemeral).map(Bundle::getId).collect(Collectors.toList());
		return ResponseEntity.ok(nodes);
	}

	@GetMapping(value = "/view")
	@Secured("ROLE_WEB")
	public SseEmitter handle() throws IOException {
		SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
		emitter.send(Map.of("type", "current", "view", messageBus.getCurrentView()));
		emitter.send(Map.of("type", "available", "view", messageBus.getCurrentAvailableView()));
		messageBus.addViewListener(new Consumer<>() {
			@Override
			public void accept(Set<NodeAddress> o) {
				try
				{
					emitter.send(Map.of("type", "current", "view", o));
				} catch (Exception e)
				{
					messageBus.removeViewListener(this);
				}
			}
		});
		messageBus.addAvailableViewListener(new Consumer<>() {
			@Override
			public void accept(Set<NodeAddress> o) {
				try
				{
					emitter.send(Map.of("type", "available", "view", o));
				} catch (Exception e)
				{
					messageBus.removeAvailableViewListener(this);
				}
			}
		});
		return emitter;
	}

	@PostMapping(value = "/spawn/{bundleId}")
	@Secured("ROLE_DEPLOY")
	public ResponseEntity<?> spawnBundle(@PathVariable String bundleId) throws Exception {
		bundleDeployService.spawn(bundleId);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping(value = "/kill/{nodeId}")
	@Secured("ROLE_DEPLOY")
	public ResponseEntity<?> killNode(@PathVariable String nodeId) throws Exception {
		bundleDeployService.kill(nodeId);
		return ResponseEntity.ok().build();
	}
}
