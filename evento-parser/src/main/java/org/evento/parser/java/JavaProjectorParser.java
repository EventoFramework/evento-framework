package org.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTAnyTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import org.evento.parser.model.component.Projector;
import org.evento.parser.model.handler.EventHandler;
import org.evento.parser.model.payload.Event;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A parser for a Java Projector component, extending the JavaComponentParser class.
 */
public class JavaProjectorParser extends JavaComponentParser<Projector> {


	/**
	 * Creates a JavaProjectorParser object.
	 *
	 * @param node the Node object representing the Java projector component
	 */
	public JavaProjectorParser(Node node) {
		super(node);
	}

	@SuppressWarnings("deprecation")
	private List<EventHandler> findEventHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("EventHandler");
		return node.getFirstChildOfType(ASTAnyTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var eventName = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					return new EventHandler(new Event(eventName), n.getBeginLine());
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
