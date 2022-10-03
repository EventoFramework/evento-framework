package org.eventrails.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import org.eventrails.parser.model.node.Saga;
import org.eventrails.parser.model.handler.SagaEventHandler;
import org.eventrails.parser.model.payload.Event;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

public class JavaSagaParser extends JavaComponentParser<Saga> {


	public JavaSagaParser(Node node) {
		super(node);
	}

	private List<SagaEventHandler> findSagaEventHandlers() throws JaxenException {
		var query =  getQueryForAnnotatedMethod("SagaEventHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var eventName = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					return new SagaEventHandler(new Event(eventName));
				}
		).collect(Collectors.toList());
	}


	public Saga parse() throws Exception {
		Saga saga = new Saga();
		saga.setComponentName(getDeclaredClassName());
		saga.setSagaEventHandlers(findSagaEventHandlers());
		findCommandInvocations(saga.getSagaEventHandlers());
		findQueryInvocations(saga.getSagaEventHandlers());
		return saga;
	}
}
