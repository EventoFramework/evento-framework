package org.evento.demo.web.domain.web;

import org.evento.application.proxy.InvokerWrapper;
import org.evento.common.modeling.annotations.component.Invoker;
import org.evento.common.modeling.annotations.handler.InvocationHandler;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.modeling.messaging.query.Single;
import org.evento.demo.api.command.DemoCreateCommand;
import org.evento.demo.api.command.DemoDeleteCommand;
import org.evento.demo.api.command.DemoUpdateCommand;
import org.evento.demo.api.query.DemoViewFindAllQuery;
import org.evento.demo.api.query.DemoViewFindByIdQuery;
import org.evento.demo.api.utils.Utils;
import org.evento.demo.api.view.DemoView;
import org.evento.demo.web.domain.web.payload.DemoPayload;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Invoker
public class DemoInvoker extends InvokerWrapper {


	@InvocationHandler
	public CompletableFuture<Collection<DemoView>> findAll(@RequestParam int page) {
		Utils.logMethodFlow(this,"findAll", page, "BEGIN");
		var resp =  getQueryGateway()
				.query(new DemoViewFindAllQuery(10, page * 10))
				.thenApply(Multiple::getData);
		Utils.logMethodFlow(this,"findAll", page, "END");
		return resp;
	}


	@InvocationHandler
	public CompletableFuture<DemoView> findById(@PathVariable String identifier) {
		Utils.logMethodFlow(this,"findById", identifier, "BEGIN");
		var resp =  getQueryGateway().query(new DemoViewFindByIdQuery(identifier)).thenApply(Single::getData);
		Utils.logMethodFlow(this,"findById", identifier, "END");
		return resp;
	}


	@InvocationHandler
	public CompletableFuture<?> save(@RequestBody DemoPayload demoPayload){
		Utils.logMethodFlow(this,"save", demoPayload, "BEGIN");
		return getCommandGateway().send(new DemoCreateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		)).thenApply(o -> {
			Utils.logMethodFlow(this,"save", demoPayload, "END");
			return o;
		});
	}


	@InvocationHandler
	public CompletableFuture<?> update(@RequestBody DemoPayload demoPayload, @PathVariable String identifier){
		Utils.logMethodFlow(this,"update", demoPayload, "BEGIN");
		demoPayload.setDemoId(identifier);
		return getCommandGateway().send(new DemoUpdateCommand(
				demoPayload.getDemoId(), demoPayload.getName(), demoPayload.getValue()
		)).thenApply(o -> {
			Utils.logMethodFlow(this,"update", demoPayload, "END");
			return o;
		});
	}


	@InvocationHandler
	public CompletableFuture<?> delete(@PathVariable String identifier){
		Utils.logMethodFlow(this,"delete", identifier, "BEGIN");
		return getCommandGateway().send(new DemoDeleteCommand(identifier
		)).thenApply(o -> {
			Utils.logMethodFlow(this,"delete", identifier, "END");
			return o;
		});
	}

}
