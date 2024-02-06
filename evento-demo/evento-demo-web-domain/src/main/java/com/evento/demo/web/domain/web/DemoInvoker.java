package com.evento.demo.web.domain.web;

import com.evento.application.performance.Track;
import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.demo.api.command.DemoCreateCommand;
import com.evento.demo.api.command.DemoDeleteCommand;
import com.evento.demo.api.command.DemoUpdateCommand;
import com.evento.demo.api.query.DemoViewFindAllQuery;
import com.evento.demo.api.query.DemoViewFindByIdQuery;
import com.evento.demo.api.utils.Utils;
import com.evento.demo.api.view.DemoView;
import com.evento.demo.web.domain.web.payload.DemoPayload;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Invoker
public class DemoInvoker extends InvokerWrapper {


	@InvocationHandler
	@Track
	public CompletableFuture<Collection<DemoView>> findAll(@RequestParam int page) {
		Utils.logMethodFlow(this, "findAll", page, "BEGIN");
		var resp = getQueryGateway()
				.query(new DemoViewFindAllQuery(10, page * 10))
				.thenApply(Multiple::getData);
		Utils.logMethodFlow(this, "findAll", page, "END");
		return resp;
	}


	@InvocationHandler
	@Track
	public CompletableFuture<DemoView> findById(@PathVariable String identifier) {
		Utils.logMethodFlow(this, "findById", identifier, "BEGIN");
		var resp = getQueryGateway().query(new DemoViewFindByIdQuery(identifier)).thenApply(Single::getData);
		Utils.logMethodFlow(this, "findById", identifier, "END");
		return resp;
	}


	@InvocationHandler
	@Track
	public CompletableFuture<?> save(@RequestBody DemoPayload demoPayload) {
		Utils.logMethodFlow(this, "save", demoPayload, "BEGIN");
		return getCommandGateway().send(new DemoCreateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		)).whenComplete((a,b) -> Utils.logMethodFlow(this, "save", demoPayload, "END"));
	}


	@InvocationHandler
	@Track
	public CompletableFuture<?> update(@RequestBody DemoPayload demoPayload, @PathVariable String identifier) {
		Utils.logMethodFlow(this, "update", demoPayload, "BEGIN");
		demoPayload.setDemoId(identifier);
		return getCommandGateway().send(new DemoUpdateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		)).thenApply(o -> {
			Utils.logMethodFlow(this, "update", demoPayload, "END");
			return o;
		});
	}


	@InvocationHandler
	@Track
	public CompletableFuture<?> delete(@PathVariable String identifier) {
		Utils.logMethodFlow(this, "delete", identifier, "BEGIN");
		return getCommandGateway().send(new DemoDeleteCommand(identifier
		)).thenApply(o -> {
			Utils.logMethodFlow(this, "delete", identifier, "END");
			return o;
		});
	}

}
