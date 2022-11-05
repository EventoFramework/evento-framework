package org.eventrails.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.eventrails.parser.model.component.Invoker;
import org.eventrails.parser.model.component.Service;
import org.eventrails.parser.model.handler.InvocationHandler;
import org.eventrails.parser.model.handler.ServiceCommandHandler;
import org.eventrails.parser.model.payload.Invocation;
import org.eventrails.parser.model.payload.ServiceCommand;
import org.eventrails.parser.model.payload.ServiceEvent;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

public class JavaInvokerParser extends JavaComponentParser<Invoker> {


	public JavaInvokerParser(Node node) {
		super(node);
	}

	private List<InvocationHandler> findInvocationHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("InvocationHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n ->{
					var name = n.getFirstParentOfType(ASTClassOrInterfaceDeclaration.class).getSimpleName() +  "::" + ((ASTMethodDeclaration) n).getName();
					return new InvocationHandler(new Invocation(name));
				}
		).collect(Collectors.toList());
	}

	public Invoker parse() throws Exception {
		Invoker invoker = new Invoker();
		invoker.setComponentName(getDeclaredClassName());
		invoker.setInvocationHandlers(findInvocationHandlers());
		findCommandInvocations(invoker.getInvocationHandlers());
		findQueryInvocations(invoker.getInvocationHandlers());
		return invoker;
	}
}
