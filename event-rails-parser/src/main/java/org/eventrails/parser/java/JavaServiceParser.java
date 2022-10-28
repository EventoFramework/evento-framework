package org.eventrails.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.eventrails.parser.model.component.Service;
import org.eventrails.parser.model.handler.ServiceCommandHandler;
import org.eventrails.parser.model.payload.ServiceCommand;
import org.eventrails.parser.model.payload.ServiceEvent;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

public class JavaServiceParser extends JavaComponentParser<Service> {


	public JavaServiceParser(Node node) {
		super(node);
	}

	private List<ServiceCommandHandler> findCommandHandler() throws JaxenException {
		var query = getQueryForAnnotatedMethod("CommandHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var commandName = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					var eventClass = md.getResultType().getFirstDescendantOfType(ASTClassOrInterfaceType.class);
					return new ServiceCommandHandler(new ServiceCommand(commandName), eventClass == null ? null : new ServiceEvent(eventClass.getImage()));
				}
		).collect(Collectors.toList());
	}


	public Service parse() throws Exception {
		Service service = new Service();
		service.setComponentName(getDeclaredClassName());
		service.setCommandHandlers(findCommandHandler());
		findCommandInvocations(service.getCommandHandlers());
		findQueryInvocations(service.getCommandHandlers());
		return service;
	}
}
