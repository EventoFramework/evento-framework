package com.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import com.evento.parser.model.component.Saga;
import com.evento.parser.model.handler.SagaEventHandler;
import com.evento.parser.model.payload.Event;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The JavaSagaParser class is responsible for parsing a Java node and extracting information to create a Saga object.
 */
public class JavaSagaParser extends JavaComponentParser<Saga> {


	/**
	 * JavaSagaParser is a class responsible for parsing a Java node and extracting information
	 * to create a Saga object.
     * @param node A node for a Saga Class
     */
	public JavaSagaParser(Node node) {
		super(node);
	}

	private List<SagaEventHandler> findSagaEventHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("SagaEventHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var eventName = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					var associationProperty = n.getParent().getChild(0)
							.findDescendantsOfType(ASTMemberValuePair.class)
							.stream()
							.filter(m -> m.getImage().equals("associationProperty"))
							.findFirst()
							.map(m -> m.getFirstDescendantOfType(ASTLiteral.class).getImage().replace("\"", ""))
							.orElseThrow();
					return new SagaEventHandler(new Event(eventName), associationProperty, n.getBeginLine());
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
