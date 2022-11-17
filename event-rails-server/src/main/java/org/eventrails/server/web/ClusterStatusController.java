package org.eventrails.server.web;

import org.eventrails.common.messaging.bus.MessageBus;
import org.eventrails.common.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.server.domain.model.BucketType;
import org.eventrails.server.domain.model.Bundle;
import org.eventrails.server.service.BundleService;
import org.eventrails.server.service.deploy.BundleDeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/cluster-status")
public class ClusterStatusController {

	private final Logger logger = LoggerFactory.getLogger(ClusterStatusController.class);

	@Value("${eventrails.cluster.node.server.name}")
	private String serverNodeName;
	private final MessageBus messageBus;

	private final BundleService bundleService;
	private final BundleDeployService bundleDeployService;

	public ClusterStatusController(MessageBus messageBus,
								   BundleService bundleService, BundleDeployService bundleDeployService) {
		this.messageBus = messageBus;
		this.bundleService = bundleService;
		this.bundleDeployService = bundleDeployService;
	}

	@GetMapping(value = "/attended-view")
	public ResponseEntity<List<String>> findAllNodes() {
		var nodes = bundleService.findAllBundles().stream().filter(Bundle::isContainsHandlers)
				.filter(b -> b.getBucketType() != BucketType.Ephemeral).map(Bundle::getName).collect(Collectors.toList());
		return ResponseEntity.ok(nodes);
	}

	@GetMapping(value = "/view")
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
	@PostMapping(value = "/spawn/{bundleName}")
	public ResponseEntity<?> spawnBundle(@PathVariable String bundleName) throws Exception {
		bundleDeployService.spawn(bundleName);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping(value = "/kill/{nodeId}")
	public ResponseEntity<?> killNode(@PathVariable String nodeId) throws Exception {
		bundleDeployService.kill(nodeId);
		return ResponseEntity.ok().build();
	}
}
