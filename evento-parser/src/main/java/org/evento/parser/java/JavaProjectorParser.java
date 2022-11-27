package org.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.evento.parser.model.component.Projector;
import org.evento.parser.model.handler.EventHandler;
import org.evento.parser.model.payload.Event;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

public class JavaProjectorParser extends JavaComponentParser<Projector> {


	public JavaProjectorParser(Node node) {
		super(node);
	}

	private List<EventHandler> findEventHandlers() throws JaxenException {
		var query =  getQueryForAnnotatedMethod("EventHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var eventName = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					return new EventHandler(new Event(eventName));
				}
		).collect(Collectors.toList());
	}


	public Projector parse() throws Exception {
		Projector projector = new Projector();
		projector.setComponentName(getDeclaredClassName());
		projector.setEventHandlers(findEventHandlers());
		findCommandInvocations(projector.getEventHandlers());
		return projector;
	}
}
