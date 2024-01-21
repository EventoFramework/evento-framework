package org.evento.parser.java;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.evento.parser.model.component.Observer;
import org.evento.parser.model.handler.EventHandler;
import org.evento.parser.model.payload.Event;
import org.jaxen.JaxenException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JavaObserverParser is a class that parses a Java file to extract information about an Observer component.
 * It extends the JavaComponentParser class and specializes in parsing Observer specific information.
 */
public class JavaObserverParser extends JavaComponentParser<Observer> {


	/**
	 * JavaObserverParser is a class that parses a Java file to extract information about an Observer component.
	 * It extends the JavaComponentParser class and specializes in parsing Observer specific information.
     * @param node a node for an observer class
     */
	public JavaObserverParser(Node node) {
		super(node);
	}

	/**
	 * Finds event handlers by searching for annotated methods with the "EventHandler" annotation.
	 *
	 * @return a list of EventHandler objects representing the event handlers found in the code
	 * @throws JaxenException if there is an error in the XPath query
	 */
	private List<EventHandler> findEventHandlers() throws JaxenException {
		var query = getQueryForAnnotatedMethod("EventHandler");
		return node.getFirstChildOfType(ASTTypeDeclaration.class).findChildNodesWithXPath(query).stream().map(
				n -> {
					var md = (ASTMethodDeclaration) n;
					var eventName = md.getFormalParameters().getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					return new EventHandler(new Event(eventName), n.getBeginLine());
				}
		).collect(Collectors.toList());
	}


	public Observer parse() throws Exception {
		Observer observer = new Observer();
		observer.setComponentName(getDeclaredClassName());
		observer.setEventHandlers(findEventHandlers());
		findCommandInvocations(observer.getEventHandlers());
		findQueryInvocations(observer.getEventHandlers());
		return observer;
	}
}
