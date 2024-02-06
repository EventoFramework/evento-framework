package com.evento.server.web;

import com.evento.server.bus.MessageBus;
import com.evento.server.bus.NodeAddress;
import com.evento.server.domain.model.core.BucketType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.service.BundleService;
import com.evento.server.service.deploy.BundleDeployService;
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

/**
 * The ClusterStatusController class is a REST controller that handles requests related to cluster status.
 * It provides endpoints for fetching cluster information and performing actions on the cluster.
 */
@RestController
@RequestMapping("api/cluster-status")
public class ClusterStatusController {

	private final Logger logger = LoggerFactory.getLogger(ClusterStatusController.class);
	private final MessageBus messageBus;
	private final BundleService bundleService;
	private final BundleDeployService bundleDeployService;

	/**
	 * The ClusterStatusController class is a REST controller that handles requests related to cluster status.
	 * It provides endpoints for fetching cluster information and performing actions on the cluster.
	 */
	public ClusterStatusController(MessageBus messageBus,
								   BundleService bundleService, BundleDeployService bundleDeployService) {
		this.messageBus = messageBus;
		this.bundleService = bundleService;
		this.bundleDeployService = bundleDeployService;
	}

	/**
	 * Finds all nodes in the system.
	 *
	 * @return A ResponseEntity containing a list of node IDs.
	 */
	@GetMapping(value = "/attended-view")
	@Secured("ROLE_WEB")
	public ResponseEntity<List<String>> findAllNodes() {
		var nodes = bundleService.findAllBundles().stream().filter(Bundle::isContainsHandlers)
				.filter(b -> b.getBucketType() != BucketType.Ephemeral).map(Bundle::getId).collect(Collectors.toList());
		return ResponseEntity.ok(nodes);
	}

	/**
	 * Handle method for the "/view" endpoint.
	 *
	 * @return SseEmitter - the server-sent event emitter for pushing data to the client.
	 * @throws IOException if an I/O error occurs.
	 */
	@GetMapping(value = "/view")
	@Secured("ROLE_WEB")
	public SseEmitter handle() throws IOException {
		SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
		emitter.send(Map.of("type", "current", "view", messageBus.getCurrentView()));
		emitter.send(Map.of("type", "available", "view", messageBus.getCurrentAvailableView()));
		messageBus.addViewListener(new Consumer<>() {
			/**
			 * Sends the current view information to the SSE emitter.
			 * If an exception occurs while sending the data, the view listener is removed.
			 *
			 * @param o The set of NodeAddress representing the current view.
			 */
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

	/**
	 * Spawns a bundle by executing a spawn script.
	 *
	 * @param bundleId The ID of the bundle to be spawned.
	 * @return A ResponseEntity representing the response status of the spawn operation.
	 * @throws Exception If an error occurs during the spawn process.
	 */
	@PostMapping(value = "/spawn/{bundleId}")
	@Secured("ROLE_DEPLOY")
	public ResponseEntity<?> spawnBundle(@PathVariable String bundleId) throws Exception {
		bundleDeployService.spawn(bundleId);
		return ResponseEntity.ok().build();
	}

	/**
	 * Deletes a node in the cluster by sending a kill message to the specified node.
	 *
	 * @param bundleId The ID of the bundle to which the node belongs.
	 * @param nodeId The ID of the node to be killed.
	 * @return A ResponseEntity representing the response status of the kill operation.
	 */
	@DeleteMapping(value = "/kill/{bundleId}/{nodeId}")
	@Secured("ROLE_DEPLOY")
	public ResponseEntity<?> killNode(@PathVariable String bundleId, @PathVariable String nodeId) {
		messageBus.sendKill(nodeId);
		return ResponseEntity.ok().build();
	}
}
