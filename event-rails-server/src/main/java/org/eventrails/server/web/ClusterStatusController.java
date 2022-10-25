package org.eventrails.server.web;

import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.NodeAddress;
import org.eventrails.server.domain.model.Ranch;
import org.eventrails.server.service.RanchApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/cluster-status")
public class ClusterStatusController {


	@Value("${eventrails.cluster.node.server.name}")
	private String serverNodeName;
	private final MessageBus messageBus;

	private final RanchApplicationService ranchApplicationService;

	public ClusterStatusController(MessageBus messageBus, RanchApplicationService ranchApplicationService) {
		this.messageBus = messageBus;
		this.ranchApplicationService = ranchApplicationService;
	}

	@GetMapping(value = "/all-nodes")
	public ResponseEntity<List<String>> findAllNodes() {
		var nodes = ranchApplicationService.findAllRanches().stream().map(Ranch::getName).collect(Collectors.toList());
		nodes.add(serverNodeName);
		return ResponseEntity.ok(nodes);
	}

	@GetMapping(value = "/view")
	public SseEmitter handle() throws IOException {
		SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
		emitter.send(messageBus.getCurrentView());
		var listener = new Consumer<List<NodeAddress>>() {
			@Override
			public void accept(List<NodeAddress> o) {
				try
				{
					emitter.send(o);
				} catch (IOException e)
				{
					e.printStackTrace();
					messageBus.removeViewListener(this);
				}
			}
		};
		messageBus.addViewListener(listener);
		return emitter;
	}

	@GetMapping(value = "/available-view")
	public SseEmitter handleViewEnabled() throws IOException {
		SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
		emitter.send(messageBus.getCurrentAvailableView());
		var listener = new Consumer<List<NodeAddress>>() {
			@Override
			public void accept(List<NodeAddress> o) {
				try
				{
					emitter.send(o);
				} catch (IOException e)
				{
					e.printStackTrace();
					messageBus.removeAvailableViewListener(this);
				}
			}
		};
		messageBus.addAvailableViewListener(listener);
		return emitter;
	}
}
