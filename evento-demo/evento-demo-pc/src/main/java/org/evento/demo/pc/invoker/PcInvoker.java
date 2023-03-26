package org.evento.demo.pc.invoker;

import org.evento.application.proxy.InvokerWrapper;
import org.evento.common.modeling.annotations.component.Invoker;
import org.evento.common.modeling.annotations.handler.InvocationHandler;
import org.evento.demo.pc.api.PcCommand1;
import org.evento.demo.pc.api.PcCommand2;

import java.util.UUID;

@Invoker
public class PcInvoker extends InvokerWrapper {

	@InvocationHandler
	public void cmd1(){
		getCommandGateway().sendAndWait(new PcCommand1("pc__" + UUID.randomUUID()));
	}

	@InvocationHandler
	public void cmd2(String id){
		getCommandGateway().sendAndWait(new PcCommand2(id));
	}
}
